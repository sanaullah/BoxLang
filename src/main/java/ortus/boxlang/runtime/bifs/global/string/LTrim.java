package ortus.boxlang.runtime.bifs.global.string;

import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.bifs.BoxMember;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.types.BoxLangType;

@BoxBIF
@BoxMember( type = BoxLangType.STRING, name = "LTrim" )
public class LTrim extends BIF {

	/**
	 * Constructor
	 */
	public LTrim() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "string", Key.string ),
		};
	}

	/**
	 * Trim leading whitespace from a string
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.string The string to trim
	 */
	public Object invoke( IBoxContext context, ArgumentsScope arguments ) {
		String input = arguments.getAsString( Key.string );
		return input.replaceAll( "^\\s+", "" );
	}
}
