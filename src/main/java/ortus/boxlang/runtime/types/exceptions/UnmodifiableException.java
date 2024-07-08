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
package ortus.boxlang.runtime.types.exceptions;

/**
 * This exception is thrown when a modification attempt is made upon an unmodifiable (e.g. final) object
 */
public class UnmodifiableException extends BoxRuntimeException {

	/**
	 * Constructor when we don't know the actual struct that was being searched
	 *
	 * @param message The message to display
	 */
	public UnmodifiableException( String message ) {
		super( message );
	}

	/**
	 * Constructor
	 *
	 * @param message The message to display
	 * @param cause   The cause
	 */
	public UnmodifiableException( String message, Throwable cause ) {
		super( message, cause );
	}

}
