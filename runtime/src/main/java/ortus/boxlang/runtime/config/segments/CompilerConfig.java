/**
 * [BoxLang]
 *
 * Copyright [2023] [Ortus Solutions, Corp]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ortus.boxlang.runtime.config.segments;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import ortus.boxlang.runtime.config.util.PlaceholderHelper;
import ortus.boxlang.runtime.types.Struct;

/**
 * The BoxLang compiler configuration
 */
public class CompilerConfig {

	/**
	 * The directory where the generated classes will be placed
	 * The default is the system temp directory + {@code /boxlang}
	 */
	public String classGenerationDirectory = System.getProperty( "java.io.tmpdir" ) + "boxlang";

	/**
	 * --------------------------------------------------------------------------
	 * Methods
	 * --------------------------------------------------------------------------
	 */

	public CompilerConfig() {
	}

	/**
	 * Processes the configuration struct. Each segment is processed individually from the initial configuration struct.
	 *
	 * @param config the configuration struct
	 *
	 * @return the configuration
	 */
	public CompilerConfig process( Struct config ) {
		// Process the class generation directory
		if ( config.containsKey( "classGenerationDirectory" ) ) {
			this.classGenerationDirectory = PlaceholderHelper.resolve( ( String ) config.get( "classGenerationDirectory" ) );
		}

		return this;
	}
}
