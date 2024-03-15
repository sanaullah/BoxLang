/**
 * [BoxLang]
 *
 * Copyright [2023] [Ortus Solutions, Corp]
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package ortus.boxlang.transpiler.transformer.expression;

import java.time.LocalDateTime;
import java.util.Map;

import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;

import ortus.boxlang.ast.BoxNode;
import ortus.boxlang.ast.BoxStatement;
import ortus.boxlang.ast.expression.BoxClosure;
import ortus.boxlang.runtime.config.util.PlaceholderHelper;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;
import ortus.boxlang.transpiler.JavaTranspiler;
import ortus.boxlang.transpiler.transformer.AbstractTransformer;
import ortus.boxlang.transpiler.transformer.TransformerContext;

public class BoxClosureTransformer extends AbstractTransformer {

	// @formatter:off
	private String template = """
		package ${packageName};

		import ortus.boxlang.runtime.scopes.IScope;
		import ortus.boxlang.runtime.scopes.Key;
		import ortus.boxlang.runtime.context.FunctionBoxContext;
		import ortus.boxlang.runtime.types.exceptions.*;
		import ortus.boxlang.runtime.context.ClassBoxContext;
		import ortus.boxlang.runtime.runnables.IBoxRunnable;
		import ortus.boxlang.runtime.context.IBoxContext;
		import ortus.boxlang.runtime.loader.ImportDefinition;
		import ortus.boxlang.runtime.interop.DynamicObject;
		import ortus.boxlang.runtime.dynamic.ExpressionInterpreter;
		import java.util.Optional;
		import ortus.boxlang.runtime.components.Component;
		import ortus.boxlang.parser.BoxScriptType;

		// Classes Auto-Imported on all Templates and Classes by BoxLang
		import java.time.LocalDateTime;
		import java.time.Instant;
		import java.lang.System;
		import java.lang.String;
		import java.lang.Character;
		import java.lang.Boolean;
		import java.lang.Double;
		import java.lang.Integer;
		import java.util.*;
		import ortus.boxlang.runtime.types.*;
		import ortus.boxlang.runtime.types.util.*;
		import java.util.LinkedHashMap;
		import java.nio.file.*;

  		public class ${classname} extends Closure {
			private static ${classname}				instance;
			private final static Key				name		= Closure.defaultName;
			private final static Argument[]			arguments	= new Argument[] {};
			private final static String				returnType	= "any";

		    private final static IStruct			annotations			= Struct.EMPTY;
			private final static IStruct			documentation		= Struct.EMPTY;
			
			private static final long					compileVersion	= ${compileVersion};
			private static final LocalDateTime			compiledOn		= ${compiledOnTimestamp};
			private static final Object					ast				= null;


			public Key getName() {
				return name;
			}
			public Argument[] getArguments() {
				return arguments;
			}
			public String getReturnType() {
				return returnType;
			}

			public Access getAccess() {
   				return Access.PUBLIC;
   			}

			public  long getRunnableCompileVersion() {
				return ${className}.compileVersion;
			}

			public LocalDateTime getRunnableCompiledOn() {
				return ${className}.compiledOn;
			}

			public Object getRunnableAST() {
				return ${className}.ast;
			}

			public ${classname}( IBoxContext declaringContext  ) {
				super( declaringContext );
			}

			@Override
			public List<ImportDefinition> getImports() {
				return imports;
      		}

			@Override
			public IStruct getAnnotations() {
				return annotations;
			}

			@Override
			public IStruct getDocumentation() {
				return documentation;
			}
			@Override
			public Object _invoke( FunctionBoxContext context ) {
				ClassLocator classLocator = ClassLocator.getInstance();

			}

			public Path getRunnablePath() {
				return ${enclosingClassName}.path;
			}

			/**
			 * The original source type
			 */
			public BoxScriptType getSourceType() {
				return ${enclosingClassName}.sourceType;
			}

  		}
 	""";
	// @formatter:on
	public BoxClosureTransformer( JavaTranspiler transpiler ) {
		super( transpiler );
	}

	/**
	 * Transform a closure declaration into a Java Class
	 *
	 * @param node    a BoxClosure AST node
	 * @param context transpiler context
	 *
	 * @return an instance of the closure subclass
	 *
	 * @throws IllegalStateException
	 */
	@Override
	public Node transform( BoxNode node, TransformerContext context ) throws IllegalStateException {
		BoxClosure			boxClosure			= ( BoxClosure ) node;
		String				packageName			= transpiler.getProperty( "packageName" );
		String				closureName			= "Closure_" + transpiler.incrementAndGetClosureCounter();
		String				enclosingClassName	= transpiler.getProperty( "classname" );
		String				className			= closureName;

		Map<String, String>	values				= Map.ofEntries(
		    Map.entry( "packageName", packageName ),
		    Map.entry( "className", className ),
		    Map.entry( "closureName", closureName ),
		    Map.entry( "enclosingClassName", enclosingClassName ),
		    Map.entry( "contextName", transpiler.peekContextName() ),
		    Map.entry( "compiledOnTimestamp", transpiler.getDateTime( LocalDateTime.now() ) ),
		    Map.entry( "compileVersion", "1L" )
		);
		transpiler.pushContextName( "context" );
		String							code	= PlaceholderHelper.resolve( template, values );
		ParseResult<CompilationUnit>	result;
		try {
			result = javaParser.parse( code );
		} catch ( Exception e ) {
			// Temp debugging to see generated Java code
			throw new BoxRuntimeException( code, e );
		}
		if ( !result.isSuccessful() ) {
			// Temp debugging to see generated Java code
			throw new BoxRuntimeException( result + "\n" + code );
		}
		CompilationUnit			javaClass		= result.getResult().get();
		/* Transform the arguments creating the initialization values */
		ArrayInitializerExpr	argInitializer	= new ArrayInitializerExpr();
		boxClosure.getArgs().forEach( arg -> {
			Expression argument = ( Expression ) transpiler.transform( arg );
			argInitializer.getValues().add( argument );
		} );
		javaClass.getType( 0 ).getFieldByName( "arguments" ).orElseThrow().getVariable( 0 ).setInitializer( argInitializer );

		/* Transform the annotations creating the initialization value */
		Expression annotationStruct = transformAnnotations( boxClosure.getAnnotations() );
		result.getResult().orElseThrow().getType( 0 ).getFieldByName( "annotations" ).orElseThrow().getVariable( 0 ).setInitializer( annotationStruct );

		MethodDeclaration	invokeMethod		= javaClass.findCompilationUnit().orElseThrow()
		    .getClassByName( className ).orElseThrow()
		    .getMethodsByName( "_invoke" ).get( 0 );

		BlockStmt			body				= invokeMethod.getBody().get();
		int					componentCounter	= transpiler.getComponentCounter();
		transpiler.setComponentCounter( 0 );
		for ( BoxStatement statement : boxClosure.getBody() ) {
			Node javaStmt = transpiler.transform( statement );
			if ( javaStmt instanceof BlockStmt stmt ) {
				stmt.getStatements().forEach( it -> body.addStatement( it ) );
			} else {
				body.addStatement( ( Statement ) javaStmt );
			}
		}
		transpiler.setComponentCounter( componentCounter );
		boolean needReturn = true;
		// ensure last statemtent in body is wrapped in a return statement if it was an expression
		if ( body.getStatements().size() > 1 ) {
			Statement lastStatement = body.getStatement( body.getStatements().size() - 1 );
			if ( lastStatement instanceof ExpressionStmt expr && ! ( expr.getExpression() instanceof VariableDeclarationExpr ) ) {
				body.getStatements().remove( lastStatement );
				body.addStatement( new ReturnStmt( new EnclosedExpr( expr.getExpression() ) ) );
				needReturn = false;
			}
		}
		// Ensure we have a return statement
		if ( needReturn ) {
			invokeMethod.getBody().get().addStatement( new ReturnStmt( new NullLiteralExpr() ) );
		}
		transpiler.popContextName();

		( ( JavaTranspiler ) transpiler ).getCallables().add( ( CompilationUnit ) javaClass );
		return parseExpression( "new ${enclosingClassName}.${closureName}( ${contextName} )", values );
	}

}
