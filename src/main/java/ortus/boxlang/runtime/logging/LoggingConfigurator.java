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
package ortus.boxlang.runtime.logging;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.Configurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggerContextAwareBase;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.Context;
import ortus.boxlang.runtime.BoxRuntime;

/**
 * Configures the bundled SLF4J provider via logback.
 *
 * THIS CLASS IS CALLED AUTOMATICALLY BY LOGBACK'S SERVICE LOADER MECHANISM.
 *
 * This class serves as a single endpoint for configuring the slf4j logging provider. Currently that is logback, but in the future it may be another
 * provider.
 *
 * See https://logback.qos.ch/manual/configuration.html for more information on logback configuration.
 */
public class LoggingConfigurator extends LoggerContextAwareBase implements Configurator {

	/**
	 * Logback-specific encoder pattern. Thankfully, this is fairly legible compared to the JUL pattern.
	 * https://logback.qos.ch/manual/layouts.html#conversionWord
	 *
	 * @see https://logback.qos.ch/manual/layouts.html#conversionWord
	 */
	public static String				LOG_FORMAT	= "%date %logger{0} [%level] %kvp %message%n";

	public static PatternLayoutEncoder	encoder;

	/**
	 * Default constructor needed by logback
	 */
	public LoggingConfigurator() {
	}

	/**
	 * Configure the logging provider.
	 * This is called by the logback service loader mechanism.
	 * This initializes the runtime's basic logging conifguration which can be expanded on.
	 * This the minimal configuration needed to get the runtime up and running.
	 *
	 * @param loggerContext The logger context to configure
	 *
	 * @return The status of the configuration
	 */
	public ExecutionStatus configure( LoggerContext loggerContext ) {

		// LoggerContext loggerContext = new LoggerContext();
		loggerContext.setName( "BoxLang" );

		encoder = new PatternLayoutEncoder();
		encoder.setContext( loggerContext );
		encoder.setPattern( LOG_FORMAT );
		encoder.start();

		// Base log level depending on debug mode
		var								debugMode	= BoxRuntime.getInstance().inDebugMode();
		Level							logLevel	= Boolean.TRUE.equals( debugMode ) ? Level.DEBUG : Level.WARN;

		// Configure a Console Appender
		// See: https://logback.qos.ch/manual/appenders.html
		ConsoleAppender<ILoggingEvent>	appender	= new ConsoleAppender<>();
		appender.setContext( loggerContext );
		appender.setEncoder( encoder );
		appender.start();

		// Configure Root Logger
		Logger rootLogger = loggerContext.getLogger( Logger.ROOT_LOGGER_NAME );
		rootLogger.setLevel( logLevel );
		rootLogger.addAppender( appender );

		setLoggerContext( loggerContext );

		// We should be the last configurator to run, so stop searching for further configurators.
		return ExecutionStatus.DO_NOT_INVOKE_NEXT_IF_ANY;

	}

	/**
	 * Enable debug mode for the runtime's root logger or not.
	 *
	 * This is usually a convenience method for the runtime to enable or disable debug mode
	 * via configuration overrides
	 *
	 * @param debugMode True to enable debug mode, false to disable
	 *
	 */
	public static void reconfigureDebugMode( Boolean debugMode ) {
		// Get root Logger from the logger context and set the log level to DEBUG
		Level	logLevel	= Boolean.TRUE.equals( debugMode ) ? Level.DEBUG : Level.INFO;
		Logger	rootLogger	= ( Logger ) LoggerFactory.getLogger( Logger.ROOT_LOGGER_NAME );
		rootLogger.setLevel( logLevel );
	}

	/**
	 * Override to set the context on the encoder
	 *
	 **/
	public void setContext( Context context ) {
		if ( this.context == null ) {
			this.context = context;
		} else if ( this.context != context ) {
			getLoggerContext().getLogger( Logger.ROOT_LOGGER_NAME )
			    .error( "LoggingConfigurator context has been already set and an attempt to overwrite was made." );
		}
	}

}
