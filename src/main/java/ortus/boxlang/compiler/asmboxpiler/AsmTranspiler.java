package ortus.boxlang.compiler.asmboxpiler;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ortus.boxlang.compiler.asmboxpiler.transformer.Transformer;
import ortus.boxlang.compiler.asmboxpiler.transformer.expression.BoxExpressionStatementTransformer;
import ortus.boxlang.compiler.asmboxpiler.transformer.expression.BoxIntegerLiteralTransformer;
import ortus.boxlang.compiler.asmboxpiler.transformer.expression.BoxStringLiteralTransformer;
import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.compiler.ast.BoxScript;
import ortus.boxlang.compiler.ast.expression.BoxIntegerLiteral;
import ortus.boxlang.compiler.ast.expression.BoxStringLiteral;
import ortus.boxlang.compiler.ast.statement.BoxExpressionStatement;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

import java.util.HashMap;

public class AsmTranspiler extends Transpiler {

	static Logger									logger		= LoggerFactory.getLogger( AsmTranspiler.class );

	private static HashMap<Class<?>, Transformer>	registry	= new HashMap<>();

	public AsmTranspiler() {
		// TODO: instance write to static field. Seems like an oversight in Java version (retained until clarified).
		registry.put( BoxStringLiteral.class, new BoxStringLiteralTransformer( this ) );
		registry.put( BoxIntegerLiteral.class, new BoxIntegerLiteralTransformer( this ) );
		registry.put( BoxExpressionStatement.class, new BoxExpressionStatementTransformer( this ) );
	}

	@Override
	public void transpile( BoxScript script, ClassVisitor classVisitor ) throws BoxRuntimeException {
		String name = "boxgenerated/scripts/Statement_7772bb7a11ca73a6556e84aabdb0e2cf"; // TODO: How to determine name?
		classVisitor.visit(
			Opcodes.V17,
			Opcodes.ACC_PUBLIC,
			name,
			null,
			"ortus/boxlang/runtime/runnables/BoxScript",
			null );
		addGetInstance( classVisitor, name );
		addConstructor( classVisitor, name );
		MethodVisitor methodVisitor = classVisitor.visitMethod(
			Opcodes.ACC_PUBLIC,
			"_invoke",
			"(Lortus/boxlang/runtime/context/IBoxContext;)Ljava/lang/Object;",
			null,
			null);
		methodVisitor.visitCode();
		methodVisitor.visitMethodInsn(
			Opcodes.INVOKESTATIC,
			"ortus/boxlang/runtime/loader/ClassLocator",
			"getInstance",
			"()Lortus/boxlang/runtime/loader/ClassLocator;",
			false );
		methodVisitor.visitIntInsn( Opcodes.ASTORE, 2 );
		script.getChildren().forEach( child -> transform( child ).accept( methodVisitor ) );
		methodVisitor.visitMaxs( -1, -1 );
		methodVisitor.visitEnd();
		// TODO: add fields and methods
	}

	private static void addConstructor( ClassVisitor classVisitor, String name ) {
		MethodVisitor methodVisitor = classVisitor.visitMethod( Opcodes.ACC_PUBLIC,
			"<init>",
			"()V",
			null,
			null );
		methodVisitor.visitCode();
		methodVisitor.visitIntInsn( Opcodes.ALOAD, 0 );
		methodVisitor.visitMethodInsn( Opcodes.INVOKESPECIAL,
			"ortus/boxlang/runtime/runnables/BoxScript",
			"<init>",
			"()V",
			false );
		methodVisitor.visitInsn( Opcodes.RETURN );
		methodVisitor.visitEnd();
	}

	private static void addGetInstance( ClassVisitor classVisitor, String name ) {
		FieldVisitor fieldVisitor = classVisitor.visitField(
			Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
			"instance",
			"L" + name + ";",
			null,
			null);
		fieldVisitor.visitEnd();
		MethodVisitor methodVisitor = classVisitor.visitMethod(
			Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNCHRONIZED | Opcodes.ACC_STATIC,
			"getInstance",
			"()L" + name + ";",
			null,
			null );
		methodVisitor.visitCode();
		Label after = new Label();
		methodVisitor.visitFieldInsn( Opcodes.GETSTATIC,
			name,
			"instance",
			"L" + name + ";" );
		methodVisitor.visitJumpInsn( Opcodes.IFNONNULL, after );
		methodVisitor.visitTypeInsn( Opcodes.NEW, name );
		methodVisitor.visitMethodInsn( Opcodes.INVOKESPECIAL,
			name,
			"<init>",
			"()V",
			false );
		methodVisitor.visitLabel( after );
		methodVisitor.visitFieldInsn( Opcodes.GETSTATIC,
			name,
			"instance",
			"L" + name + ";" );
		methodVisitor.visitInsn( Opcodes.ARETURN );
		methodVisitor.visitMaxs(-1, -1);
		methodVisitor.visitEnd();
	}

	@Override
	public AbstractInsnNode transform(BoxNode node ) {
		Transformer transformer = registry.get( node.getClass() );
		if ( transformer != null ) {
			return transformer.transform( node );
		}
		throw new IllegalStateException( "unsupported: " + node.getClass().getSimpleName() + " : " + node.getSourceText() );
	}
}
