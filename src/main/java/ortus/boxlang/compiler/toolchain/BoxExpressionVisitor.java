package ortus.boxlang.compiler.toolchain;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ErrorNode;
import ortus.boxlang.compiler.ast.*;
import ortus.boxlang.compiler.ast.expression.*;
import ortus.boxlang.compiler.ast.statement.BoxAnnotation;
import ortus.boxlang.compiler.ast.statement.BoxArgumentDeclaration;
import ortus.boxlang.compiler.parser.BoxScriptParser;
import ortus.boxlang.parser.antlr.BoxScriptGrammar;
import ortus.boxlang.parser.antlr.BoxScriptGrammarBaseVisitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BoxExpressionVisitor extends BoxScriptGrammarBaseVisitor<BoxExpression> {

	private final BoxScriptParser	tools;
	private final BoxVisitor		statementVisitor;

	public BoxExpressionVisitor( BoxScriptParser tools, BoxVisitor statementVisitor ) {
		this.tools				= tools;
		this.statementVisitor	= statementVisitor;
	}

	/**
	 * This is here simply to allow tests to resolve a single expression without having to walk exprStaments
	 * @param ctx the parse tree
	 * @return the expression
	 */
	public BoxExpression visitTestExpression( BoxScriptGrammar.TestExpressionContext ctx ) {
		return ctx.expression().accept( this );
	}

	public BoxExpression visitInvocable( BoxScriptGrammar.InvocableContext ctx ) {
		return ctx.el2().accept( this );
	}

	/**
	 * Manufactures an AST node that indicates that the wrapped expression is in parentheses.
	 * <p>
	 * <p>
	 * Generally, one does not explicitly put this in an AST. If we need to regenerate the source we can see that
	 * an expression was parenthesised because the operator precedence will be different. However,
	 * in some cases it is useful to have this information in the AST, for instance if we wish to preserve
	 * redundant parentheses.
	 * </p>
	 *
	 * @param ctx the parse tree
	 *
	 * @return The AST for the parenthesised expression
	 */
	@Override
	public BoxExpression visitExprPrecedence( BoxScriptGrammar.ExprPrecedenceContext ctx ) {
		var	pos	= tools.getPosition( ctx );
		var	src	= tools.getSourceText( ctx );

		return new BoxParenthesis( ctx.expression().accept( this ), pos, src );
	}

	@Override
	public BoxExpression visitExprUnary( BoxScriptGrammar.ExprUnaryContext ctx ) {
		var	pos		= tools.getPosition( ctx );
		var	src		= tools.getSourceText( ctx );
		var	right	= ctx.el2().accept( this );
		var	op		= switch ( ctx.op.getType() ) {
						case BoxScriptGrammar.PLUS -> BoxUnaryOperator.Plus;
						case BoxScriptGrammar.MINUS -> BoxUnaryOperator.Minus;
						case BoxScriptGrammar.NOT -> BoxUnaryOperator.Not;
						case BoxScriptGrammar.BANG -> BoxUnaryOperator.Not;
						default -> null;  // Cannot happen - satisfy the compiler
					};
		return new BoxUnaryOperation( right, op, pos, src );
	}

	@Override
	public BoxExpression visitExprPostfix( BoxScriptGrammar.ExprPostfixContext ctx ) {
		var	pos		= tools.getPosition( ctx );
		var	src		= tools.getSourceText( ctx );
		var	left	= ctx.el2().accept( this );
		var	op		= switch ( ctx.op.getType() ) {
						case BoxScriptGrammar.PLUSPLUS -> BoxUnaryOperator.PostPlusPlus;
						case BoxScriptGrammar.MINUSMINUS -> BoxUnaryOperator.PostMinusMinus;
						default -> null;  // Cannot happen - satisfy the compiler
					};
		return new BoxUnaryOperation( left, op, pos, src );
	}

	@Override
	public BoxExpression visitExprPrefix( BoxScriptGrammar.ExprPrefixContext ctx ) {
		var	pos		= tools.getPosition( ctx );
		var	src		= tools.getSourceText( ctx );
		var	right	= ctx.el2().accept( this );
		var	op		= switch ( ctx.op.getType() ) {
						case BoxScriptGrammar.PLUSPLUS -> BoxUnaryOperator.PrePlusPlus;
						case BoxScriptGrammar.MINUSMINUS -> BoxUnaryOperator.PreMinusMinus;
						case BoxScriptGrammar.BITWISE_COMPLEMENT -> BoxUnaryOperator.BitwiseComplement;
						default -> null;  // Cannot happen - satisfy the compiler
					};
		return new BoxUnaryOperation( right, op, pos, src );
	}

	public BoxExpression visitExprDotFloat( BoxScriptGrammar.ExprDotFloatContext ctx ) {

		var	left	= ctx.el2().accept( this );
		var	dotLit	= ctx.DOT_FLOAT_LITERAL();
		var	right	= new BoxIntegerLiteral( dotLit.getText().substring( 1 ),
		    tools.getPosition( dotLit ), dotLit.getText() );
		var	pos		= tools.getPosition( dotLit );
		var	src		= dotLit.getText();

		// Because Booleans take precedence over keywords as identifiers, we will get a
		// boolean literal for left or right and so we convert them to Identifiers if that is
		// the case. As other types may also need conversion, we hand off to a helper method.
		var	leftId	= convertDotElement( left, false );

		tools.checkDotAccess( leftId, right );

		return new BoxDotAccess( leftId, ctx.QM() != null, right, pos, src );
	}

	/**
	 * visits the Dot accessor operation and generates the relevant AST.
	 * <p>
	 * With dot accessors, there some special cases where the left and right are folded
	 * into one node rather than encapsulated into a DotAccess. For example method
	 * invocations will be seen on the right of x.y() and the invocation AST for y()
	 * will have the x reference folded into it.
	 * </p>
	 *
	 * @param ctx the parse tree
	 *
	 * @return the AST for a particular accessor operation
	 */
	@Override
	public BoxExpression visitExprDotAccess( BoxScriptGrammar.ExprDotAccessContext ctx ) {

		// Positions is based upon the right hand side of the dot, but strangely, includes the dot
		var	pos		= tools.getPosition( ctx.el2( 1 ) );
		var	start	= pos.getStart();
		start.setColumn( start.getColumn() - 1 );
		var	src		= "." + tools.getSourceText( ctx.el2( 1 ) );
		var	left	= ctx.el2( 0 ).accept( this );
		var	right	= ctx.el2( 1 ).accept( this );

		// Because Booleans take precedence over keywords as identifiers, we will get a
		// boolean literal for left or right and so we convert them to Identifiers if that is
		// the case. As other types may also need conversion, we hand off to a helper method.
		var	leftId	= convertDotElement( left, false );
		var	rightId	= convertDotElement( right, true );

		// Check validity of this type of access. Will add an issue to the list if there is an invalid access
		// but we will still generate a DotAccess node and carry on, so we catch all errors in one pass.
		tools.checkDotAccess( leftId, rightId );

		switch ( right ) {

			case BoxMethodInvocation invocation -> {

				// If we are in a chain of c.geta().getb(), then we need to chase down the chain of invocations because
				// the method invocation we are looking at will be the last getObj() in the chain, and we need to
				// replace it with a method invocation on the left side of the dot access.
				var	endChain	= findLastInvocation( invocation );

				var	target		= endChain.getObj();
				if ( target instanceof BoxFunctionInvocation funcInvoc ) {

					// A simple function invocation now becomes a method invocation on the left side, but we adjust
					// text and position to reflect the dot access
					funcInvoc.setSourceText( "." + funcInvoc.getSourceText() );
					var	iName	= funcInvoc.getName();

					// Have to adjust the positions for the invocation and Identifier
					var	is		= funcInvoc.getPosition().getStart();
					var	ids		= new Point( is.getLine(), is.getColumn() );
					var	ide		= funcInvoc.getPosition().getEnd();
					ide = new Point( ide.getLine(), ids.getColumn() + iName.length() );

					// Account for the dot in the invocation
					is.setColumn( is.getColumn() + -1 );

					// The Id needs to end in the correct column, not the end of the invocation
					var	iPos	= new Position( ids, ide );

					// Some messing around to get the text correct for the MethodInvocation
					// as FunctionInvocation only stores a string, not an identifier for the function for
					// some reason.
					var	mi		= new BoxMethodInvocation(
					    new BoxIdentifier( iName, iPos, iName ),
					    left,
					    funcInvoc.getArguments(), ctx.QM() != null, true,
					    funcInvoc.getPosition(), funcInvoc.getSourceText() );

					// This invocation now replace the function invocation at the end of the chain with the method invocation
					endChain.setObj( mi );

					// And we return the original invocation, which is now the top of the chain
					return invocation;

				}
				// The method invocation needs to know what it is being invoked upon
				endChain.setObj( left );
				endChain.setSafe( ctx.QM() != null );
				return invocation;
			}

			case BoxFunctionInvocation invocation -> {
				// A simple function invocation now becomes a method invocation on the left side, but we adjust
				// text and position to reflect the dot access
				invocation.setSourceText( "." + invocation.getSourceText() );
				var	iName	= invocation.getName();

				// Have to adjust the positions for the invocation and Identifier
				var	is		= invocation.getPosition().getStart();
				var	ids		= new Point( is.getLine(), is.getColumn() );
				var	ide		= invocation.getPosition().getEnd();
				ide = new Point( ide.getLine(), ids.getColumn() + iName.length() );

				// Account for the dot in the invocation
				is.setColumn( is.getColumn() + -1 );

				// The Id needs to end in the correct column, not the end of the invocation
				var iPos = new Position( ids, ide );

				// Some messing around to get the text correct for the MethodInvocation
				// as FunctionInvocation only stores a string, not an identifier for the function for
				// some reason.
				return new BoxMethodInvocation(
				    new BoxIdentifier( iName, iPos, iName ),
				    left,
				    invocation.getArguments(), ctx.QM() != null, true,
				    invocation.getPosition(), invocation.getSourceText() );
			}

			case BoxExpressionInvocation invocation -> {

				// When we are dot accessing an expression invocation, we need to cater for a chain of invocations
				// because c.y()()() is valid syntax. The Method invocation will be on the last invocation as we see
				// them in the tree in the order they will be traversed: the last invocation is the top of the tree
				// and will be invoked on the result of the previous one. We then return the top of the invocation chain.
				// If this is the only one, then it is folded into the method invocation.

				// Find the last expression invocation in the chain. We cannot go straight to the function invocation
				// because it's node has no way to reference the previous invocation, and we need to replace it and
				// set the new node as the target expression of the last expression invocation.
				var endChain = findLastInvocation( invocation );

				// There was a chain of expression invocations, and this endChain should be a function invocation
				if ( endChain.getExpr() instanceof BoxFunctionInvocation funcInvoc ) {

					// A simple function invocation now becomes a method invocation on the left side, but we adjust
					// text and position to reflect the dot access
					funcInvoc.setSourceText( "." + funcInvoc.getSourceText() );
					var	iName	= funcInvoc.getName();

					// Have to adjust the positions for the invocation and Identifier
					var	is		= funcInvoc.getPosition().getStart();
					var	ids		= new Point( is.getLine(), is.getColumn() );
					var	ide		= funcInvoc.getPosition().getEnd();
					ide = new Point( ide.getLine(), ids.getColumn() + iName.length() );

					// Account for the dot in the invocation
					is.setColumn( is.getColumn() + -1 );

					// The Id needs to end in the correct column, not the end of the invocation
					var	iPos	= new Position( ids, ide );

					// Some messing around to get the text correct for the MethodInvocation
					// as FunctionInvocation only stores a string, not an identifier for the function for
					// some reason.
					var	mi		= new BoxMethodInvocation(
					    new BoxIdentifier( iName, iPos, iName ),
					    left,
					    funcInvoc.getArguments(), ctx.QM() != null, true,
					    funcInvoc.getPosition(), funcInvoc.getSourceText() );

					// This invocation now replace the function invocation at the end of the chain with the method invocation
					endChain.setExpr( mi );

					// And we return the original invocation, which is now the top of the chain
					return invocation;
				}

				// The invocation is the last in the chain, so we fold it into the method invocation
				// TODO: Check source settings etc

				invocation.setSourceText( "." + invocation.getSourceText() );
				var expr = invocation.getExpr();

				return new BoxMethodInvocation(
				    left,
				    expr,
				    invocation.getArguments(), ctx.QM() != null, true,
				    invocation.getPosition(), invocation.getSourceText() );

			}
			case BoxArrayAccess arrayAccess -> {

				return new BoxArrayAccess( leftId, ctx.QM() != null, arrayAccess.getAccess(), pos, src );
			}
			case null, default -> {

				return new BoxDotAccess( leftId, ctx.QM() != null, rightId, pos, src );
			}
		}
	}

	@Override
	public BoxExpression visitExprHeadless( BoxScriptGrammar.ExprHeadlessContext ctx ) {
		List<BoxArgument>	arguments	= null;
		var					pos			= tools.getPosition( ctx );
		var					src			= tools.getSourceText( ctx );
		if ( ctx.LPAREN() != null ) {
			arguments = Optional.ofNullable( ctx.argumentList() )
			    .map( argumentList -> argumentList.argument().stream().map( arg -> ( BoxArgument ) arg.accept( this ) ).toList() )
			    .orElse( Collections.emptyList() );
		}
		return new BoxFunctionalMemberAccess( ctx.identifier().getText(), arguments, pos, src );
	}

	@Override
	public BoxExpression visitExprBIF( BoxScriptGrammar.ExprBIFContext ctx ) {
		var	pos	= tools.getPosition( ctx );
		var	src	= tools.getSourceText( ctx );
		return new BoxFunctionalBIFAccess( ctx.identifier().getText(), pos, src );
	}

	@Override
	public BoxExpression visitExprPower( BoxScriptGrammar.ExprPowerContext ctx ) {
		var	pos		= tools.getPosition( ctx );
		var	src		= tools.getSourceText( ctx );
		var	left	= ctx.el2( 0 ).accept( this );
		var	right	= ctx.el2( 1 ).accept( this );
		return new BoxBinaryOperation( left, BoxBinaryOperator.Power, right, pos, src );
	}

	@Override
	public BoxExpression visitExprMult( BoxScriptGrammar.ExprMultContext ctx ) {
		var	pos		= tools.getPosition( ctx );
		var	src		= tools.getSourceText( ctx );
		var	left	= ctx.el2( 0 ).accept( this );
		var	right	= ctx.el2( 1 ).accept( this );
		var	op		= switch ( ctx.op.getType() ) {
						case BoxScriptGrammar.STAR -> BoxBinaryOperator.Star;
						case BoxScriptGrammar.SLASH -> BoxBinaryOperator.Slash;
						case BoxScriptGrammar.MOD, BoxScriptGrammar.PERCENT -> BoxBinaryOperator.Mod;
						case BoxScriptGrammar.BACKSLASH -> BoxBinaryOperator.Backslash;
						default -> null;  // Cannot happen - satisfy the compiler
					};
		return new BoxBinaryOperation( left, op, right, pos, src );
	}

	@Override
	public BoxExpression visitExprAdd( BoxScriptGrammar.ExprAddContext ctx ) {
		var	pos		= tools.getPosition( ctx );
		var	src		= tools.getSourceText( ctx );
		var	left	= ctx.el2( 0 ).accept( this );
		var	right	= ctx.el2( 1 ).accept( this );
		var	op		= switch ( ctx.op.getType() ) {
						case BoxScriptGrammar.PLUS -> BoxBinaryOperator.Plus;
						case BoxScriptGrammar.MINUS -> BoxBinaryOperator.Minus;
						default -> null; // Cannot happen - satisfy the compiler
					};
		return new BoxBinaryOperation( left, op, right, pos, src );
	}

	@Override
	public BoxExpression visitExprBitShift( BoxScriptGrammar.ExprBitShiftContext ctx ) {
		var	pos		= tools.getPosition( ctx );
		var	src		= tools.getSourceText( ctx );
		var	left	= ctx.el2( 0 ).accept( this );
		var	right	= ctx.el2( 1 ).accept( this );
		var	op		= switch ( ctx.op.getType() ) {
						case BoxScriptGrammar.BITWISE_SIGNED_LEFT_SHIFT -> BoxBinaryOperator.BitwiseSignedLeftShift;
						case BoxScriptGrammar.BITWISE_SIGNED_RIGHT_SHIFT -> BoxBinaryOperator.BitwiseSignedRightShift;
						case BoxScriptGrammar.BITWISE_UNSIGNED_RIGHT_SHIFT -> BoxBinaryOperator.BitwiseUnsignedRightShift;
						default -> null;  // Cannot happen - satisfy the compiler
					};
		return new BoxBinaryOperation( left, op, right, pos, src );
	}

	@Override
	public BoxExpression visitExprBinary( BoxScriptGrammar.ExprBinaryContext ctx ) {
		var	pos		= tools.getPosition( ctx );
		var	src		= tools.getSourceText( ctx );
		var	left	= ctx.el2( 0 ).accept( this );
		var	right	= ctx.el2( 1 ).accept( this );
		var	op		= buildBinOp( ctx.binOps() );
		return new BoxBinaryOperation( left, op, right, pos, src );
	}

	@Override
	public BoxExpression visitExprBAnd( BoxScriptGrammar.ExprBAndContext ctx ) {
		var	pos		= tools.getPosition( ctx );
		var	src		= tools.getSourceText( ctx );
		var	left	= ctx.el2( 0 ).accept( this );
		var	right	= ctx.el2( 1 ).accept( this );
		return new BoxBinaryOperation( left, BoxBinaryOperator.BitwiseAnd, right, pos, src );
	}

	@Override
	public BoxExpression visitExprBXor( BoxScriptGrammar.ExprBXorContext ctx ) {
		var	pos		= tools.getPosition( ctx );
		var	src		= tools.getSourceText( ctx );
		var	left	= ctx.el2( 0 ).accept( this );
		var	right	= ctx.el2( 1 ).accept( this );
		return new BoxBinaryOperation( left, BoxBinaryOperator.BitwiseXor, right, pos, src );
	}

	@Override
	public BoxExpression visitExprBor( BoxScriptGrammar.ExprBorContext ctx ) {
		var	pos		= tools.getPosition( ctx );
		var	src		= tools.getSourceText( ctx );
		var	left	= ctx.el2( 0 ).accept( this );
		var	right	= ctx.el2( 1 ).accept( this );
		return new BoxBinaryOperation( left, BoxBinaryOperator.BitwiseOr, right, pos, src );
	}

	@Override
	public BoxExpression visitExprRelational( BoxScriptGrammar.ExprRelationalContext ctx ) {
		var	pos		= tools.getPosition( ctx );
		var	src		= tools.getSourceText( ctx );
		var	left	= ctx.el2( 0 ).accept( this );
		var	right	= ctx.el2( 1 ).accept( this );
		var	op		= buildRelOp( ctx.relOps() );
		return new BoxComparisonOperation( left, op, right, pos, src );
	}

	@Override
	public BoxExpression visitExprEqual( BoxScriptGrammar.ExprEqualContext ctx ) {
		var	pos		= tools.getPosition( ctx );
		var	src		= tools.getSourceText( ctx );
		var	left	= ctx.el2( 0 ).accept( this );
		var	right	= ctx.el2( 1 ).accept( this );
		return new BoxComparisonOperation( left, BoxComparisonOperator.Equal, right, pos, src );
	}

	@Override
	public BoxExpression visitExprXor( BoxScriptGrammar.ExprXorContext ctx ) {
		var	pos		= tools.getPosition( ctx );
		var	src		= tools.getSourceText( ctx );
		var	left	= ctx.el2( 0 ).accept( this );
		var	right	= ctx.el2( 1 ).accept( this );
		return new BoxBinaryOperation( left, BoxBinaryOperator.Xor, right, pos, src );
	}

	@Override
	public BoxExpression visitExprCat( BoxScriptGrammar.ExprCatContext ctx ) {
		var					pos		= tools.getPosition( ctx );
		var					src		= tools.getSourceText( ctx );
		List<BoxExpression>	parts;

		var					left	= ctx.el2( 0 ).accept( this );
		var					right	= ctx.el2( 1 ).accept( this );

		// If the left is a concat, we can just add the right to it to chain the concatenation. The
		// code generator should check the parts and if both left and right are literal strings, then
		// it should concatenate them into a single string before code generation.
		if ( left instanceof BoxStringConcat concat ) {
			concat.getValues().add( right );
			concat.setValues( concat.getValues() );  // Cause parents to be reset
			return concat;
		}

		// If the left is not a concat, we need to create a new one
		parts = new ArrayList<>();
		parts.add( left );
		parts.add( right );

		return new BoxStringConcat( parts, pos, src );
	}

	@Override
	public BoxExpression visitExprNotContains( BoxScriptGrammar.ExprNotContainsContext ctx ) {
		var	pos		= tools.getPosition( ctx );
		var	src		= tools.getSourceText( ctx );
		var	left	= ctx.el2( 0 ).accept( this );
		var	right	= ctx.el2( 1 ).accept( this );
		return new BoxBinaryOperation( left, BoxBinaryOperator.NotContains, right, pos, src );
	}

	@Override
	public BoxExpression visitExprAnd( BoxScriptGrammar.ExprAndContext ctx ) {
		var	pos		= tools.getPosition( ctx );
		var	src		= tools.getSourceText( ctx );
		var	left	= ctx.el2( 0 ).accept( this );
		var	right	= ctx.el2( 1 ).accept( this );
		return new BoxBinaryOperation( left, BoxBinaryOperator.And, right, pos, src );
	}

	@Override
	public BoxExpression visitExprOr( BoxScriptGrammar.ExprOrContext ctx ) {
		var	pos		= tools.getPosition( ctx );
		var	src		= tools.getSourceText( ctx );
		var	left	= ctx.el2( 0 ).accept( this );
		var	right	= ctx.el2( 1 ).accept( this );
		return new BoxBinaryOperation( left, BoxBinaryOperator.Or, right, pos, src );
	}

	/**
	 * Generate the ELVIS AST node.
	 *
	 * @param bermudaTriangle the parse tree
	 *
	 * @return The binary operation representing Elvis
	 *
	 * @apiNote Elvis needs boats
	 */
	@Override
	public BoxExpression visitExprElvis( BoxScriptGrammar.ExprElvisContext bermudaTriangle ) {
		var	pos			= tools.getPosition( bermudaTriangle );
		var	src			= tools.getSourceText( bermudaTriangle );
		var	elvisDock	= bermudaTriangle.el2( 0 ).accept( this );
		var	boat		= bermudaTriangle.el2( 1 ).accept( this );
		return new BoxBinaryOperation( elvisDock, BoxBinaryOperator.Elvis, boat, pos, src );
	}

	@Override
	public BoxExpression visitExprInstanceOf( BoxScriptGrammar.ExprInstanceOfContext ctx ) {
		var	pos		= tools.getPosition( ctx );
		var	src		= tools.getSourceText( ctx );
		var	left	= ctx.el2( 0 ).accept( this );
		var	right	= ctx.el2( 1 ).accept( this );
		return new BoxBinaryOperation( left, BoxBinaryOperator.InstanceOf, right, pos, src );
	}

	@Override
	public BoxExpression visitExprCastAs( BoxScriptGrammar.ExprCastAsContext ctx ) {
		var	pos		= tools.getPosition( ctx );
		var	src		= tools.getSourceText( ctx );
		var	left	= ctx.el2( 0 ).accept( this );
		var	right	= ctx.el2( 1 ).accept( this );
		return new BoxBinaryOperation( left, BoxBinaryOperator.CastAs, right, pos, src );
	}

	@Override
	public BoxExpression visitExprTernary( BoxScriptGrammar.ExprTernaryContext ctx ) {
		var	pos			= tools.getPosition( ctx );
		var	src			= tools.getSourceText( ctx );
		var	condition	= ctx.el2( 0 ).accept( this );
		var	trueExpr	= ctx.el2( 1 ).accept( this );
		var	falseExpr	= ctx.el2( 2 ).accept( this );
		return new BoxTernaryOperation( condition, trueExpr, falseExpr, pos, src );
	}

	@Override
	public BoxExpression visitExprAssign( BoxScriptGrammar.ExprAssignContext ctx ) {
		var	pos		= tools.getPosition( ctx );
		var	src		= tools.getSourceText( ctx );
		var	left	= ctx.el2().accept( this );
		var	right	= ctx.expression().accept( this );
		var	op		= buildAssignOp( ctx.op );
		return new BoxAssignment( left, op, right, List.of(), pos, src );
	}

	@Override
	public BoxExpression visitExprOutString( BoxScriptGrammar.ExprOutStringContext ctx ) {
		return ctx.el2().accept( this );
	}

	@Override
	public BoxExpression visitExprArrayAccess( BoxScriptGrammar.ExprArrayAccessContext ctx ) {
		var	pos		= tools.getPosition( ctx );
		var	src		= tools.getSourceText( ctx );
		var	object	= ctx.el2().accept( this );
		var	access	= ctx.expression().accept( this );

		// Check that the access is valid as not everything can be an array. note
		// that an invalid access will not stop the AST from being generated, but no
		// codegen will happen from it.
		tools.checkArrayAccess( ctx, object, access );

		return new BoxArrayAccess( object, false, access, pos, src );
	}

	@Override
	public BoxExpression visitExprArrayLiteral( BoxScriptGrammar.ExprArrayLiteralContext ctx ) {
		return ctx.arrayLiteral().accept( this );
	}

	@Override
	public BoxExpression visitArrayLiteral( BoxScriptGrammar.ArrayLiteralContext ctx ) {
		var	pos		= tools.getPosition( ctx );
		var	src		= tools.getSourceText( ctx );
		var	values	= Optional.ofNullable( ctx.expressionList() )
		    .map( expressionList -> expressionList.expression().stream().map( expr -> expr.accept( this ) ).collect( Collectors.toList() ) )
		    .orElse( Collections.emptyList() );
		return new BoxArrayLiteral( values, pos, src );
	}

	@Override
	public BoxExpression visitAttributeSimple( BoxScriptGrammar.AttributeSimpleContext ctx ) {
		var				pos		= tools.getPosition( ctx );
		var				src		= tools.getSourceText( ctx );

		BoxExpression	value	= null;
		if ( ctx.annotation() != null ) {
			return ctx.annotation().accept( this );
		}
		if ( ctx.identifier() != null ) {
			// Converting an identifier to a string literal here in the AST removes ambiguity, but also loses the
			// lexical context of the original source code.
			return new BoxStringLiteral( ctx.identifier().getText(), pos, src );
		}
		// Converting an fqn to a string literal here in the AST removes ambiguity, but also loses the
		// lexical context of the original source code.
		return new BoxStringLiteral( ctx.fqn().getText(), pos, src );
	}

	@Override
	public BoxExpression visitAnnotation( BoxScriptGrammar.AnnotationContext ctx ) {
		if ( ctx.atoms() != null ) {
			return ctx.atoms().accept( this );
		}

		if ( ctx.structExpression() != null ) {
			return ctx.structExpression().accept( this );
		}

		if ( ctx.stringLiteral() != null ) {
			return ctx.stringLiteral().accept( this );
		}
		// Then it must be this
		return ctx.arrayLiteral().accept( this );
	}

	@Override
	public BoxExpression visitClosureFunc( BoxScriptGrammar.ClosureFuncContext ctx ) {
		var								pos				= tools.getPosition( ctx );
		var								src				= tools.getSourceText( ctx );
		List<BoxArgumentDeclaration>	params			= Optional.ofNullable( ctx.functionParamList() ).map( paramList -> paramList.functionParam().stream()
		    .map( param -> ( BoxArgumentDeclaration ) param.accept( statementVisitor ) ).collect( Collectors.toList() ) ).orElse( Collections.emptyList() );

		var								body			= ctx.normalStatementBlock().accept( statementVisitor );

		List<BoxAnnotation>				postAnnotations	= Optional
		    .ofNullable( ctx.postAnnotation() ).map( postAnnotationList -> postAnnotationList.stream()
		        .map( postAnnotation -> ( BoxAnnotation ) postAnnotation.accept( statementVisitor ) ).collect( Collectors.toList() ) )
		    .orElse( Collections.emptyList() );

		return new BoxClosure( params, postAnnotations, ( BoxStatement ) body, pos, src );
	}

	@Override
	public BoxExpression visitLambdaFunc( BoxScriptGrammar.LambdaFuncContext ctx ) {
		var								pos				= tools.getPosition( ctx );
		var								src				= tools.getSourceText( ctx );

		// The parameters are either a single identifier or a list of parameters, but will never be both.
		// So rather than have lots of if statements, we can just concatenate the two streams, the identifier
		// stream only ever returning one element.
		List<BoxArgumentDeclaration>	params			= Stream.concat(
		    Optional.ofNullable( ctx.identifier() )
		        .map( identifier -> new BoxArgumentDeclaration( false, "Any", identifier.getText(), null, new ArrayList<>(), new ArrayList<>(),
		            tools.getPosition( identifier ), tools.getSourceText( identifier ) ) )
		        .stream(),
		    Optional.ofNullable( ctx.functionParamList() )
		        .map( paramList -> paramList.functionParam().stream().map( param -> ( BoxArgumentDeclaration ) param.accept( statementVisitor ) ) )
		        .orElseGet( Stream::empty ) )
		    .collect( Collectors.toList() );

		var								body			= ctx.statement().accept( statementVisitor );

		List<BoxAnnotation>				postAnnotations	= Optional
		    .ofNullable( ctx.postAnnotation() ).map( postAnnotationList -> postAnnotationList.stream()
		        .map( postAnnotation -> ( BoxAnnotation ) postAnnotation.accept( statementVisitor ) ).collect( Collectors.toList() ) )
		    .orElse( Collections.emptyList() );

		// The lambdaFunc rule will trigger even if it is actually a closure because
		// the only difference in syntax is the definition operator
		if ( ctx.ARROW_RIGHT() != null ) {
			return new BoxClosure( params, postAnnotations, ( BoxStatement ) body, pos, src );
		} else {
			return new BoxLambda( params, postAnnotations, ( BoxStatement ) body, pos, src );
		}
	}

	@Override
	public BoxExpression visitExprFunctionCall( BoxScriptGrammar.ExprFunctionCallContext ctx ) {
		var	pos		= tools.getPosition( ctx );
		var	src		= tools.getSourceText( ctx );
		var	name	= ctx.el2().accept( this );
		var	args	= Optional.ofNullable( ctx.argumentList() )
		    .map( argumentList -> argumentList.argument().stream().map( arg -> ( BoxArgument ) arg.accept( this ) ).toList() )
		    .orElse( Collections.emptyList() );

		// if a simple name was given, then it's a simple function call (which may be converted to method in
		// the dot handler. Expressions will sometimes come through as their primitive types.
		if ( name instanceof BoxIdentifier || name instanceof BoxBooleanLiteral || name instanceof BoxNull || name instanceof BoxScope ) {
			return new BoxFunctionInvocation( name.getSourceText(), args, pos, src );
		}

		// if the function is invoked upon what at this point looks like an array access, but the array access was
		// on a literal, then it is actually not an array access, but a method invocation on the object literal.
		if ( name instanceof BoxArrayAccess arrayAccess ) {

			var		array	= arrayAccess.getContext();
			String	prefix	= array.getSourceText();
			String	lbs		= src.substring( prefix.length() );
			var		start	= arrayAccess.getPosition().getStart();
			start.setColumn( start.getColumn() + prefix.length() );
			var	end		= arrayAccess.getPosition().getEnd();
			var	argsEnd	= pos.getEnd();
			end.setColumn( argsEnd.getColumn() );
			end.setLine( argsEnd.getLine() );
			arrayAccess.setSourceText( lbs );

			return new BoxMethodInvocation( arrayAccess.getAccess(), array, args, false, false, arrayAccess.getPosition(), lbs );
		}

		// It was not a simple named function or method, so for now, we assume expression invocation
		return new BoxExpressionInvocation( name, args, tools.getPosition( ctx.LPAREN().getSymbol(), ctx.RPAREN().getSymbol() ),
		    tools.getSourceText( ctx.LPAREN().getSymbol(), ctx.RPAREN().getSymbol() ) );
	}

	@Override
	public BoxExpression visitArgument( BoxScriptGrammar.ArgumentContext ctx ) {
		var	pos	= tools.getPosition( ctx );
		var	src	= tools.getSourceText( ctx );
		if ( ctx.namedArgument() != null ) {
			return ctx.namedArgument().accept( this );
		}
		return ctx.positionalArgument().accept( this );
	}

	@Override
	public BoxExpression visitNamedArgument( BoxScriptGrammar.NamedArgumentContext ctx ) {
		var				pos	= tools.getPosition( ctx );
		var				src	= tools.getSourceText( ctx );
		BoxExpression	name;
		if ( ctx.identifier() != null ) {
			name = new BoxStringLiteral( ctx.identifier().getText(), pos, src );
		} else {
			name = ctx.stringLiteral().accept( this );
		}

		BoxExpression value = ctx.expression().accept( this );
		return new BoxArgument( name, value, pos, src );
	}

	@Override
	public BoxExpression visitPositionalArgument( BoxScriptGrammar.PositionalArgumentContext ctx ) {
		var	pos	= tools.getPosition( ctx );
		var	src	= tools.getSourceText( ctx );
		return new BoxArgument( ctx.expression().accept( this ), pos, src );
	}

	@Override
	public BoxExpression visitIdentifier( BoxScriptGrammar.IdentifierContext ctx ) {
		var	pos	= tools.getPosition( ctx );
		var	src	= tools.getSourceText( ctx );
		if ( tools.isScope( ctx.getText() ) ) {
			return new BoxScope( ctx.getText(), pos, src );
		}
		return new BoxIdentifier( ctx.getText(), pos, src );
	}

	@Override
	public BoxExpression visitExprStaticAccess( BoxScriptGrammar.ExprStaticAccessContext ctx ) {
		var	pos		= tools.getPosition( ctx );
		var	src		= tools.getSourceText( ctx );

		var	left	= ctx.el2( 0 ).accept( this );
		if ( left instanceof BoxDotAccess ) {
			left = new BoxFQN( ctx.el2( 0 ).getText(), tools.getPosition( ctx.el2( 0 ) ), tools.getSourceText( ctx.el2( 0 ) ) );
		}
		var right = ctx.el2( 1 ).accept( this );

		if ( right instanceof BoxFunctionInvocation invocation ) {
			return new BoxStaticMethodInvocation( new BoxIdentifier( invocation.getName(), invocation.getPosition(), invocation.getSourceText() ), left,
			    invocation.getArguments(), pos, src );
		}
		return new BoxStaticAccess( left, false, right, tools.getPosition( ctx.el2( 1 ) ), "::" + tools.getSourceText( ctx.el2( 1 ) ) );
	}

	@Override
	public BoxExpression visitExprNew( BoxScriptGrammar.ExprNewContext ctx ) {
		return ctx.new_().accept( this );
	}

	@Override
	public BoxExpression visitNew( BoxScriptGrammar.NewContext ctx ) {
		var					pos		= tools.getPosition( ctx );
		var					src		= tools.getSourceText( ctx );
		BoxIdentifier		prefix;

		List<BoxArgument>	args	= Optional.ofNullable( ctx.argumentList() )
		    .map( argumentList -> argumentList.argument().stream().map( arg -> ( BoxArgument ) arg.accept( this ) ).toList() )
		    .orElse( Collections.emptyList() );

		BoxExpression		expr	= Optional.ofNullable( ctx.fqn() ).map( fqn -> fqn.accept( this ) ).orElseGet( () -> ctx.stringLiteral().accept( this ) );

		prefix = ( BoxIdentifier ) Optional.ofNullable( ctx.preFix() ).map( preFix -> preFix.identifier().accept( this ) ).orElse( null );

		return new BoxNew( prefix, expr, args, pos, src );
	}

	@Override
	public BoxExpression visitExprLiterals( BoxScriptGrammar.ExprLiteralsContext ctx ) {
		return ctx.literals().accept( this );
	}

	/**
	 * Visit the identifier context to generate the AST node for the identifier.
	 * <p>
	 * TODO: Note that the original code check to build a scope but then other AST constructors
	 * just deconstruct it. There may not need to be a separate scope object.
	 * </p>
	 *
	 * @param ctx the parse tree
	 *
	 * @return Either a BoxIdentifier or BoxScope AST node
	 */
	@Override
	public BoxExpression visitExprIdentifier( BoxScriptGrammar.ExprIdentifierContext ctx ) {
		if ( tools.isScope( ctx.getText() ) ) {
			return new BoxScope( ctx.getText(), tools.getPosition( ctx ), ctx.getText() );
		}
		return new BoxIdentifier( ctx.getText(), tools.getPosition( ctx ), ctx.getText() );
	}

	@Override
	public BoxExpression visitLiterals( BoxScriptGrammar.LiteralsContext ctx ) {
		return Optional.ofNullable( ctx.stringLiteral() ).map( c -> c.accept( this ) ).orElseGet( () -> ctx.structExpression().accept( this ) );
	}

	@Override
	public BoxExpression visitStringLiteral( BoxScriptGrammar.StringLiteralContext ctx ) {
		var	pos			= tools.getPosition( ctx );
		var	src			= tools.getSourceText( ctx );
		var	quoteChar	= ctx.getText().substring( 0, 1 );
		var	text		= ctx.getText().substring( 1, ctx.getText().length() - 1 );

		if ( ctx.expression().isEmpty() ) {
			return new BoxStringLiteral( tools.escapeStringLiteral( quoteChar, text ), pos, src );
		}

		var parts = ctx.children.stream()
		    .filter( it -> it instanceof BoxScriptGrammar.StringLiteralPartContext || it instanceof BoxScriptGrammar.ExpressionContext )
		    .map( it -> it instanceof BoxScriptGrammar.StringLiteralPartContext
		        ? new BoxStringLiteral( tools.escapeStringLiteral( quoteChar, tools.getSourceText( ( ParserRuleContext ) it ) ),
		            tools.getPosition( ( ParserRuleContext ) it ), tools.getSourceText( ( ParserRuleContext ) it ) )
		        : it.accept( this ) )
		    .toList();

		return new BoxStringInterpolation( parts, pos, src );
	}

	@Override
	public BoxExpression visitStructExpression( BoxScriptGrammar.StructExpressionContext ctx ) {
		var					pos				= tools.getPosition( ctx );
		var					src				= tools.getSourceText( ctx );
		var					type			= ctx.RBRACKET() != null ? BoxStructType.Ordered : BoxStructType.Unordered;
		var					structMembers	= ctx.structMembers();
		List<BoxExpression>	values			= new ArrayList<>();

		if ( structMembers != null ) {
			boolean isKey = true;
			for ( BoxScriptGrammar.StructMemberContext structMember : structMembers.structMember() ) {
				values.add( structMember.structKey().accept( this ) );
				values.add( structMember.expression().accept( this ) );
			}
		}

		return new BoxStructLiteral( type, values, pos, src );
	}

	@Override
	public BoxExpression visitStructKey( BoxScriptGrammar.StructKeyContext ctx ) {
		var	pos	= tools.getPosition( ctx );
		var	src	= tools.getSourceText( ctx );
		return Optional.ofNullable( ctx.identifier() ).map( id -> id.accept( this ) )
		    .orElseGet( () -> Optional.ofNullable( ctx.stringLiteral() ).map( str -> str.accept( this ) ).orElse( new BoxIntegerLiteral( src, pos, src ) ) );
	}

	@Override
	public BoxExpression visitExprAtoms( BoxScriptGrammar.ExprAtomsContext ctx ) {
		return ctx.atoms().accept( this );
	}

	@Override
	public BoxExpression visitAtoms( BoxScriptGrammar.AtomsContext ctx ) {
		var	pos	= tools.getPosition( ctx.a );
		var	src	= tools.getSourceText( ctx );
		return switch ( ctx.a.getType() ) {
			case BoxScriptGrammar.NULL -> new BoxNull( pos, src );
			case BoxScriptGrammar.TRUE -> new BoxBooleanLiteral( true, pos, src );
			case BoxScriptGrammar.FALSE -> new BoxBooleanLiteral( false, pos, src );
			case BoxScriptGrammar.INTEGER_LITERAL -> new BoxIntegerLiteral( src, pos, src );
			case BoxScriptGrammar.FLOAT_LITERAL, BoxScriptGrammar.DOT_FLOAT_LITERAL -> new BoxDecimalLiteral( src, pos, src );
			default -> null;  // Cannot happen - satisfy the compiler
		};
	}

	@Override
	public BoxExpression visitFqn( BoxScriptGrammar.FqnContext ctx ) {
		var	pos	= tools.getPosition( ctx );
		var	src	= tools.getSourceText( ctx );
		return new BoxFQN( ctx.getText(), pos, src );
	}

	// ==============================================================================================
	// Builder methods
	//
	// Builders perform specialized task for the visitor functions where the task
	// is too complex to be done inline or otherwise obfuscates what the visitor is doing

	// Find the last BoxExpressionInvocation in a chain of invocations
	private BoxExpressionInvocation findLastInvocation( BoxExpressionInvocation invocation ) {
		var current = invocation;
		while ( current.getExpr() instanceof BoxExpressionInvocation ) {
			current = ( BoxExpressionInvocation ) current.getExpr();
		}
		return current;
	}

	private BoxMethodInvocation findLastInvocation( BoxMethodInvocation invocation ) {
		var current = invocation;
		while ( current.getObj() instanceof BoxMethodInvocation ) {
			current = ( BoxMethodInvocation ) current.getObj();
		}
		return current;
	}

	private <T> void processIfNotNull( List<T> list, Consumer<T> consumer ) {
		Optional.ofNullable( list ).ifPresent( l -> l.forEach( consumer ) );
	}

	/**
	 * Visit the relational operator context to generate the AST node for the operator.
	 * <p>
	 * As the operations seem to have grown in the telling so to speak, there are
	 * some wierd and wonderful combinations that are utterly superfluous even as
	 * syntactic sugar. However, as they are in the language, we must deal with them.
	 * </p>
	 *
	 * @param ctx the parse tree
	 *
	 * @return the operation AST node
	 */
	public BoxComparisonOperator buildRelOp( BoxScriptGrammar.RelOpsContext ctx ) {

		// Convert the context to a string without whitespace. Then we can just have a string
		// switch
		var op = ctx.getText().replaceAll( "\\s+", "" ).toUpperCase();

		return switch ( op ) {
			case "GT", ">", "GREATERTHAN" -> BoxComparisonOperator.GreaterThan;
			case "GTE", ">=", "GE", "GREATERTHANOREQTO", "GREATERTHANOREQUALTO" ->
			    BoxComparisonOperator.GreaterThanEquals;
			case "===" -> BoxComparisonOperator.TEqual;
			case "LE", "<=", "LTE", "LESSTHANOREQTO", "LESSTHANOREQUALTO" -> BoxComparisonOperator.LessThanEquals;
			case "LT", "<", "LESSTHAN" -> BoxComparisonOperator.LessThan;
			case "NE", "!=", "NOTEQUAL", "ISNOT", "<>" -> BoxComparisonOperator.NotEqual;
			default -> null; // Cannot happen - satisfy the compiler
		};
	}

	/**
	 * Visit the relational operator context to generate the AST node for the operator.
	 * <p>
	 * As the operations seem to have grown in the telling so to speak, there are
	 * some wierd and wonderful combinations that are utterly superfluous even as
	 * syntactic sugar. However, as they are in the language, we must deal with them.
	 * </p>
	 *
	 * @param ctx the parse tree
	 *
	 * @return the operation AST node
	 */
	public BoxBinaryOperator buildBinOp( BoxScriptGrammar.BinOpsContext ctx ) {

		// Convert the context to a string without whitespace. Then we can just have a string
		// switch
		var op = ctx.getText().replaceAll( "\\s+", "" ).toUpperCase();

		return switch ( op ) {
			case "EQV" -> BoxBinaryOperator.Equivalence;
			case "IMP" -> BoxBinaryOperator.Implies;
			case "CONTAINS" -> BoxBinaryOperator.Contains;
			case "NOTCONTAINS" -> BoxBinaryOperator.NotContains;
			default -> // Cannot happen - satisfy the compiler
			    null;
		};
	}

	/**
	 * Build the assignment operator from the token
	 *
	 * @param token The token to build the operator from
	 *
	 * @return The BoxAssignmentOperator AST
	 */
	private BoxAssignmentOperator buildAssignOp( Token token ) {
		return switch ( token.getType() ) {
			case BoxScriptGrammar.EQUALSIGN -> BoxAssignmentOperator.Equal;
			case BoxScriptGrammar.PLUSEQUAL -> BoxAssignmentOperator.PlusEqual;
			case BoxScriptGrammar.MINUSEQUAL -> BoxAssignmentOperator.MinusEqual;
			case BoxScriptGrammar.STAREQUAL -> BoxAssignmentOperator.StarEqual;
			case BoxScriptGrammar.SLASHEQUAL -> BoxAssignmentOperator.SlashEqual;
			case BoxScriptGrammar.MODEQUAL -> BoxAssignmentOperator.ModEqual;
			case BoxScriptGrammar.CONCATEQUAL -> BoxAssignmentOperator.ConcatEqual;
			default -> null;  // Cannot happen without grammar change - satisfy the compiler
		};
	}

	/**
	 * Build the scope for a new expression, given that we know the prefix should
	 * be a scope. Also used by the Identifier builder to build the scope if it detects
	 * that the identifier is one of the predefined scopes.
	 * <p>
	 * Note that this function does not check that the scope is valid and that should
	 * be done in the verification pass.
	 * </p>
	 *
	 * @param prefix The possibly COLON-suffixed string for scope generation.
	 *
	 * @return The BoxScope AST
	 */
	private BoxExpression buildScope( Token prefix ) {
		var scope = prefix.getText().replaceAll( "[:]+$", "" ).toUpperCase();
		return new BoxScope( scope, tools.getPosition( prefix ), prefix.getText() );
	}

	/**
	 * Convert a dot element to its intended meaning, as it may be something else
	 * when not in a dot accessor.
	 *
	 * @param expr The expression to convert
	 *
	 * @return The converted expression
	 */
	private BoxExpression convertDotElement( BoxExpression expr, boolean withScope ) {
		if ( expr instanceof BoxBooleanLiteral || expr instanceof BoxNull || ( withScope && expr instanceof BoxScope ) ) {
			return new BoxIdentifier( expr.getSourceText(), expr.getPosition(), expr.getSourceText() );
		}
		return expr;
	}

	/**
	 * Builds the correct type for a value key or value in a struct literal.
	 *
	 * @param ctx the ParserContext to accept and convert
	 *
	 * @return the correct BoxType
	 */
	private BoxExpression buildKey( BoxScriptGrammar.ExpressionContext ctx ) {
		var expr = ctx.accept( this );
		if ( expr instanceof BoxBooleanLiteral || expr instanceof BoxNull || expr instanceof BoxScope ) {
			return new BoxIdentifier( expr.getSourceText(), expr.getPosition(), expr.getSourceText() );
		}
		return expr;
	}

	/**
	 * A special node is inserted into the tree when a parser error is encountered. Here we provide
	 * a visitor to handle such nodes so that we can call the visitor even when the parser rejected the input
	 *
	 * @param node the error node
	 *
	 * @return a New error node so that AST building can work
	 */
	@Override
	public BoxExpression visitErrorNode( ErrorNode node ) {
		var err = new BoxExpressionError( tools.getPosition( node ), node.getText() );
		tools.reportExpressionError( err );
		return err;
	}
}
