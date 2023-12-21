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
package ortus.boxlang.runtime.bifs.global.type;

import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.bifs.BoxMember;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.dynamic.casters.ArrayCaster;
import ortus.boxlang.runtime.dynamic.casters.CastAttempt;
import ortus.boxlang.runtime.dynamic.casters.StringCaster;
import ortus.boxlang.runtime.dynamic.casters.StructCaster;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.BoxLangType;
import ortus.boxlang.runtime.types.Struct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

@BoxBIF // Len()
@BoxBIF( alias = "StructCount" )
@BoxBIF( alias = "ArrayLen" )
@BoxMember( type = BoxLangType.STRUCT, name = "count" )
@BoxMember( type = BoxLangType.STRUCT, name = "len" )
@BoxMember( type = BoxLangType.ARRAY )
@BoxMember( type = BoxLangType.STRING )
// TODO: Query
public class Len extends BIF {

	/**
	 * Constructor
	 */
	public Len() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "any", Key.value )
		};
	}

	/**
	 * Returns the absolute value of a number
	 * 
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 * 
	 * @argument.value The number to return the absolute value of
	 */
	public Object invoke( IBoxContext context, ArgumentsScope arguments ) {
		Object				object			= arguments.get( Key.value );
		CastAttempt<Array>	arrayAttempt	= ArrayCaster.attempt( object );
		if ( arrayAttempt.wasSuccessful() ) {
			return arrayAttempt.get().size();
		}
		CastAttempt<Struct> structAttempt = StructCaster.attempt( object );
		if ( structAttempt.wasSuccessful() ) {
			return structAttempt.get().size();
		}
		CastAttempt<String> stringAttempt = StringCaster.attempt( object );
		if ( stringAttempt.wasSuccessful() ) {
			return stringAttempt.get().length();
		}
		// TODO: Queries
		throw new BoxRuntimeException( "Cannot determine length of object of type " + object.getClass().getName() );
	}

}
