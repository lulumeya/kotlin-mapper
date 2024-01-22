package mapper.kotlin

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import kotlin.reflect.KClass


@Suppress("unused", "RedundantVisibilityModifier")
public annotation class Mapper(vararg val targetType: KClass<*>)

internal class DataClassMapper : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return DataClassMapperProcessor(environment.codeGenerator, environment.logger)
    }
}

private class DataClassMapperProcessor(
    val codeGenerator: CodeGenerator,
    val logger: KSPLogger
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols: Sequence<KSAnnotated> =
            resolver.getSymbolsWithAnnotation(Mapper::class.java.name)
        symbols
            .filter { it is KSClassDeclaration }
            .forEach {
                it.accept(BuilderVisitor(), Unit)
            }
        return listOf()
    }

    inner class BuilderVisitor : KSVisitorVoid() {

        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
            classDeclaration.annotations.filter {
                it.annotationType.element?.run {
                    this is KSClassifierReference && this.referencedName() == Mapper::class.simpleName
                } ?: false
            }.forEach { annotation ->
                annotation.arguments.mapNotNull { it.value }.forEach { arguments ->
                    if (arguments is KSType) {
                        val declaration = arguments.declaration
                        if (declaration is KSClassDeclaration) {
                            generateMapperFunction(
                                targetClasses = listOf(declaration),
                                originClass = classDeclaration
                            )
                        }
                    } else if (arguments is Collection<*>) {
                        generateMapperFunction(
                            targetClasses = arguments.mapNotNull { (it as? KSType)?.declaration as? KSClassDeclaration },
                            originClass = classDeclaration
                        )
                    }
                }
            }
        }

        private fun generateMapperFunction(
            targetClasses: List<KSClassDeclaration>,
            originClass: KSClassDeclaration
        ) {
            val originParams: List<KSValueParameter> =
                originClass.primaryConstructor?.parameters ?: return

            val packageName = originClass.containingFile!!.packageName.asString()
            val className = "${originClass.simpleName.asString()}Mapper"
            val fileSpec = FileSpec.builder(packageName, className).indent("\t")

            targetClasses.forEach {
                val targetParams: List<KSValueParameter> =
                    it.primaryConstructor?.parameters ?: return
                val funSpec = createFunSpec(it, originClass, targetParams, originParams)
                fileSpec.addFunction(funSpec.build())
            }

            fileSpec.build().writeTo(
                codeGenerator = codeGenerator,
                dependencies = Dependencies(false)
            )
        }

        private fun createFunSpec(
            targetClass: KSClassDeclaration,
            originClass: KSClassDeclaration,
            targetParams: List<KSValueParameter>,
            originParams: List<KSValueParameter>
        ): FunSpec.Builder {
            val targetClassName = targetClass.simpleName.asString()
            val functionName =
                "mapTo${targetClassName.replaceFirstChar { it.uppercase() }}"
            logger.warn("generating mapper function $functionName which can map a ${originClass.toClassName()} class to ${targetClass.toClassName()}")
            val funSpec = FunSpec.builder(functionName)
                .receiver(originClass.toClassName())
                .returns(targetClass.toClassName())

            val paramsWithDefaultValue = mutableListOf<KSValueParameter>()
            val paramsWithoutDefaultValue = mutableListOf<KSValueParameter>()

            targetParams.forEach { target ->
                originParams.find {
                    it.name!!.asString() == target.name!!.asString() && it.type.toTypeName() == target.type.toTypeName()
                }?.run {
                    paramsWithDefaultValue.add(target)
                } ?: let {
                    paramsWithoutDefaultValue.add(target)
                }
            }
            funSpec
                .addStatement(
                    "return ${targetClass.qualifiedName!!.asString()}(\n\t\t${
                        targetParams.joinToString(
                            separator = ",\n\t\t"
                        ) {
                            val parameterName = it.name!!.asString()
                            "$parameterName = $parameterName"
                        }
                    })"
                )
            paramsWithoutDefaultValue.forEach {
                val name = it.name!!.asString()
                funSpec.addParameter(
                    ParameterSpec.builder(
                        name = name,
                        type = it.type.toTypeName()
                    ).build()
                )
            }
            paramsWithDefaultValue.forEach { parameter ->
                val name = parameter.name!!.asString()
                val defaultValue = originClass.getDeclaredProperties()
                    .find { it.simpleName.asString() == name }?.simpleName
                val builder = ParameterSpec.builder(
                    name = name,
                    type = parameter.type.toTypeName()
                )
                defaultValue?.run {
                    builder.defaultValue(
                        "%L",
                        "this.$name"
                    )
                }
                funSpec.addParameter(
                    builder.build()
                )
            }
            return funSpec
        }
    }
}
