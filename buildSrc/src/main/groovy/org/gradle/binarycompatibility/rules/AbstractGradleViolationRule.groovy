/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.binarycompatibility.rules

import com.github.javaparser.JavaParser
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.visitor.GenericVisitorAdapter
import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import japicmp.model.JApiClass
import japicmp.model.JApiCompatibility
import japicmp.model.JApiField
import japicmp.model.JApiHasAnnotations
import japicmp.model.JApiMethod
import me.champeau.gradle.japicmp.report.AbstractContextAwareViolationRule
import me.champeau.gradle.japicmp.report.Violation
import org.gradle.binarycompatibility.AcceptedApiChanges
import org.gradle.binarycompatibility.ApiChange
import org.gradle.util.GradleVersion

@CompileStatic
abstract class AbstractGradleViolationRule extends AbstractContextAwareViolationRule {

    private final Map<ApiChange, String> acceptedApiChanges
    private final currentVersion

    AbstractGradleViolationRule(Map<String, String> acceptedApiChanges) {
        this.acceptedApiChanges = AcceptedApiChanges.fromAcceptedChangesMap(acceptedApiChanges)
        this.currentVersion = GradleVersion.current().baseVersion.version
    }

    private static boolean isAnnotatedWithIncubating(JApiHasAnnotations member) {
        member.annotations*.fullyQualifiedName.any { it == 'org.gradle.api.Incubating' }
    }

    private static boolean isAnnotatedWithDeprecated(JApiHasAnnotations member) {
        member.annotations*.fullyQualifiedName.any { it == 'java.lang.Deprecated'}
    }

    boolean isIncubatingOrDeprecated(JApiHasAnnotations member) {
        if (member instanceof JApiClass) {
            return isIncubatingOrDeprecated((JApiClass) member)
        } else if (member instanceof JApiMethod) {
            return isIncubatingOrDeprecatedOrOverride((JApiMethod) member)
        } else if (member instanceof JApiField) {
            return isIncubatingOrDeprecated((JApiField) member)
        }
        return isAnnotatedWithIncubating(member)
    }

    boolean isIncubatingOrDeprecated(JApiClass clazz) {
        return isAnnotatedWithIncubating(clazz) || isAnnotatedWithDeprecated(clazz)
    }

    boolean isIncubatingOrDeprecated(JApiField field) {
        return isAnnotatedWithIncubating(field) || isAnnotatedWithIncubating(field.jApiClass) || isAnnotatedWithDeprecated(field) || isAnnotatedWithDeprecated(field.jApiClass)
    }

    boolean isIncubatingOrDeprecatedOrOverride(JApiMethod method) {
        return isAnnotatedWithIncubating(method) || isAnnotatedWithIncubating(method.jApiClass) || isOverride(method) || isAnnotatedWithDeprecated(method) || isAnnotatedWithDeprecated(method.jApiClass)
    }

    boolean isDeprecated(JApiClass clazz) {
        return isAnnotatedWithDeprecated(clazz)
    }

    boolean isDeprecated(JApiField field) {
        return isAnnotatedWithDeprecated(field) || isAnnotatedWithDeprecated(field.jApiClass)
    }

    boolean isDeprecated(JApiMethod method) {
        return isAnnotatedWithDeprecated(method) || isAnnotatedWithDeprecated(method.jApiClass)
    }

    boolean isOverride(JApiMethod method) {
        // @Override has source retention - so we need to peek into the sources
        def visitor = new GenericVisitorAdapter<Object, Void>() {
            @Override
            Object visit(MethodDeclaration declaration, Void arg) {
                if (declaration.name == method.name && declaration.annotations.any { it.name.name == "Override" } ) {
                    return new Object()
                }
                return null
            }
        }
        return JavaParser.parse(sourceFileFor(method.jApiClass.fullyQualifiedName)).accept(visitor, null) != null
    }

    File sourceFileFor(String className) {
        List<String> sourceFolders = context.userData.get("apiSourceFolders") as List<String>
        for (String sourceFolder : sourceFolders) {
            def sourceFile = new File("$sourceFolder/${className.replace('.', '/')}.java")
            if (sourceFile.exists()) {
                return sourceFile
            }
        }
        throw new RuntimeException("No source file found for: $className")
    }

    Violation acceptOrReject(JApiCompatibility member, Violation rejection) {
        Set<ApiChange> seenApiChanges = (Set<ApiChange>) context.userData["seenApiChanges"]
        List<String> changes = member.compatibilityChanges.collect { Violation.describe(it) }
        def change = new ApiChange(
            context.className,
            Violation.describe(member),
            changes
        )
        String acceptationReason = acceptedApiChanges[change]
        if (acceptationReason != null) {
            seenApiChanges.add(change)
            return Violation.accept(member, "${rejection.getHumanExplanation()}. Reason for accepting this: <b>$acceptationReason</b>")
        }
        def acceptanceJson = new LinkedHashMap<String, Object>([
            type: change.type,
            member: change.member,
            acceptation: '&lt;ADD YOUR CUSTOM REASON HERE&gt;'
        ])
        if (change.changes) {
            acceptanceJson.changes = change.changes
        }

        def id = "accept" + (change.type + change.member).replaceAll('[^a-zA-Z0-9]', '_')
        Violation violation = Violation.error(
            member,
            rejection.getHumanExplanation() + """. If you did this intentionally, you need to accept the change and explain yourself:
                <a class="btn btn-info" role="button" data-toggle="collapse" href="#${id}" aria-expanded="false" aria-controls="collapseExample">Accept this change</a>
                <div class="collapse" id="${id}">
                  <div class="well">
                      In order to accept this change add the following to <code>subprojects/distributions/src/changes/accepted-public-api-changes.json</code>:
                    <pre>${JsonOutput.prettyPrint(JsonOutput.toJson(acceptanceJson))}</pre>
                  </div>
                </div>""".stripIndent()
        )
        return violation
    }

    String getCurrentVersion() {
        return currentVersion
    }
}
