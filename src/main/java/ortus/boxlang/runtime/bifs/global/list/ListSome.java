
package ortus.boxlang.runtime.bifs.global.list;

import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.bifs.BoxMember;
import ortus.boxlang.runtime.bifs.global.array.ArraySome;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.types.BoxLangType;
import ortus.boxlang.runtime.types.ListUtil;

@BoxBIF
@BoxMember( type = BoxLangType.STRING, name = "listSome" )

public class ListSome extends ArraySome {

	/**
	 * Constructor
	 */
	public ListSome() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "string", Key.list ),
		    new Argument( true, "function", Key.callback ),
		    new Argument( false, "string", Key.delimiter, ListUtil.DEFAULT_DELIMITER ),
		    new Argument( false, "boolean", Key.includeEmptyFields, false ),
		    new Argument( false, "boolean", Key.multiCharacterDelimiter, true ),
		    new Argument( false, "boolean", Key.parallel, false ),
		    new Argument( false, "integer", Key.maxThreads )
		};
	}

	/**
	 * Tests whether any item in a list meets the specified callback
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.list string list to filter entries from
	 *
	 * @argument.closure function closure test to execute
	 *
	 * @argument.delimiter string the list delimiter
	 *
	 * @argument.includeEmptyFields boolean whether to include empty fields in the returned result
	 *
	 * @argument.multiCharacterDelimiter boolean whether the delimiter is multi-character
	 *
	 * @argument.parallel boolean whether to execute the filter in parallel
	 *
	 * @argument.maxThreads number the maximum number of threads to use in the parallel filter
	 */
	public Object invoke( IBoxContext context, ArgumentsScope arguments ) {
		arguments.put(
		    Key.array,
		    ListUtil.asList(
		        arguments.getAsString( Key.list ),
		        arguments.getAsString( Key.delimiter ),
		        arguments.getAsBoolean( Key.includeEmptyFields ),
		        arguments.getAsBoolean( Key.multiCharacterDelimiter )
		    )
		);
		return super.invoke( context, arguments );
	}

}
