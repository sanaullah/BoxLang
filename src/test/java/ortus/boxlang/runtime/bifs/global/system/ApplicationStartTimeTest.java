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
package ortus.boxlang.runtime.bifs.global.system;

import static com.google.common.truth.Truth.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.application.Application;
import ortus.boxlang.runtime.context.ApplicationBoxContext;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.ScriptingRequestBoxContext;
import ortus.boxlang.runtime.scopes.IScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.scopes.VariablesScope;
import ortus.boxlang.runtime.services.ApplicationService;
import ortus.boxlang.runtime.types.DateTime;

public class ApplicationStartTimeTest {

	static BoxRuntime			instance;
	static ApplicationService	applicationService;
	IBoxContext					context;
	IScope						variables;
	static Key					result	= new Key( "result" );

	@BeforeAll
	public static void setUp() {
		instance			= BoxRuntime.getInstance( true );
		applicationService	= instance.getApplicationService();
	}

	@AfterAll
	public static void teardown() {

	}

	@BeforeEach
	void setupEach() {
		context		= new ScriptingRequestBoxContext( instance.getRuntimeContext() );
		variables	= context.getScopeNearby( VariablesScope.name );

	}

	@DisplayName( "It can stop an application" )
	@Test
	void testItCanStopAnApplication() {

		Application targetApp = applicationService.getApplication( Key.of( "unit-test" ) );
		assertThat( targetApp.hasStarted() ).isTrue();

		Instant					startTime	= targetApp.getStartTime();

		ApplicationBoxContext	appContext	= new ApplicationBoxContext( targetApp );
		appContext.setParent( instance.getRuntimeContext() );
		context.setParent( appContext );

		instance.executeSource(
		    """
		    results = applicationStartTime();
		    """,
		    context );

		assertThat( variables.get( Key.of( "results" ) ).toString() ).isEqualTo( new DateTime( startTime ).toString() );
	}
}