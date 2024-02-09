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
package ortus.boxlang.debugger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.SocketException;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.ObjectReference;

import ortus.boxlang.debugger.event.Event;
import ortus.boxlang.debugger.event.StoppedEvent;
import ortus.boxlang.debugger.request.ConfigurationDoneRequest;
import ortus.boxlang.debugger.request.ContinueRequest;
import ortus.boxlang.debugger.request.IDebugRequest;
import ortus.boxlang.debugger.request.InitializeRequest;
import ortus.boxlang.debugger.request.LaunchRequest;
import ortus.boxlang.debugger.request.ScopeRequest;
import ortus.boxlang.debugger.request.SetBreakpointsRequest;
import ortus.boxlang.debugger.request.StackTraceRequest;
import ortus.boxlang.debugger.request.ThreadsRequest;
import ortus.boxlang.debugger.request.VariablesRequest;
import ortus.boxlang.debugger.response.ContinueResponse;
import ortus.boxlang.debugger.response.InitializeResponse;
import ortus.boxlang.debugger.response.NoBodyResponse;
import ortus.boxlang.debugger.response.ScopeResponse;
import ortus.boxlang.debugger.response.SetBreakpointsResponse;
import ortus.boxlang.debugger.response.StackTraceResponse;
import ortus.boxlang.debugger.response.ThreadsResponse;
import ortus.boxlang.debugger.response.VariablesResponse;
import ortus.boxlang.debugger.types.Breakpoint;
import ortus.boxlang.debugger.types.Scope;
import ortus.boxlang.debugger.types.Source;
import ortus.boxlang.debugger.types.StackFrame;
import ortus.boxlang.debugger.types.Variable;
import ortus.boxlang.runtime.BoxRunner;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.runnables.compiler.JavaBoxpiler;
import ortus.boxlang.runtime.runnables.compiler.SourceMap;
import ortus.boxlang.runtime.util.JsonUtil;

/**
 * Implements Microsoft's Debug Adapter Protocol https://microsoft.github.io/debug-adapter-protocol/
 */
public class DebugAdapter {

	private Thread						inputThread;
	private Logger						logger;
	private InputStream					inputStream;
	private OutputStream				outputStream;
	private BoxLangDebugger				debugger;
	private boolean						running		= true;
	private List<IDebugRequest>			requestQueue;
	private JavaBoxpiler				javaBoxpiler;

	private Map<Integer, ScopeCache>	seenScopes	= new HashMap<Integer, ScopeCache>();

	/**
	 * Constructor
	 * 
	 * @param debugClient The socket that handles communication with the debug tool
	 */
	public DebugAdapter( InputStream inputStream, OutputStream outputStream ) {
		this.logger			= LoggerFactory.getLogger( BoxRuntime.class );
		this.inputStream	= inputStream;
		this.outputStream	= outputStream;
		this.requestQueue	= new ArrayList<IDebugRequest>();
		this.javaBoxpiler	= JavaBoxpiler.getInstance();

		try {
			createInputListenerThread();
			startMainLoop();
		} catch ( IOException e ) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * Used to determin if the debug session has completed.
	 * 
	 * @return
	 */
	public boolean isRunning() {
		return this.running;
	}

	private void startMainLoop() {
		while ( this.running ) {
			processDebugProtocolMessages();

			if ( this.debugger != null ) {
				this.debugger.keepWorking();
			}
		}
	}

	private void processDebugProtocolMessages() {
		// while ( requestQueue.size() > 0 ) {
		// IDebugRequest request = null;
		// synchronized ( requestQueue ) {
		// if ( requestQueue.size() > 0 ) {
		// request = requestQueue.remove( 0 );
		// }
		// }
		// if ( request != null ) {
		// request.accept( this );

		// }
		// }

		synchronized ( this ) {
			while ( requestQueue.size() > 0 ) {

				IDebugRequest request = null;
				if ( requestQueue.size() > 0 ) {
					request = requestQueue.remove( 0 );
				}

				if ( request != null ) {
					request.accept( this );
				}
			}

		}
	}

	/**
	 * Starts a new thread to wait for messages from the debug client. Each message will deserialized and then visited.
	 * 
	 * @throws IOException
	 */
	private void createInputListenerThread() throws IOException {
		InputStreamReader	iSR		= new InputStreamReader( this.inputStream );
		BufferedReader		bR		= new BufferedReader( iSR );
		DebugAdapter		adapter	= this;

		this.inputThread = new Thread( new Runnable() {

			@Override
			public void run() {
				while ( true ) {
					try {
						String line = bR.readLine();
						System.out.println( line );
						Pattern	p	= Pattern.compile( "Content-Length: (\\d+)" );
						Matcher	m	= p.matcher( line );

						if ( m.find() ) {
							int			contentLength	= Integer.parseInt( m.group( 1 ) );
							CharBuffer	buf				= CharBuffer.allocate( contentLength );

							bR.readLine();
							bR.read( buf );

							IDebugRequest request = parseDebugRequest( new String( buf.array() ) );

							if ( request != null ) {
								synchronized ( adapter ) {
									requestQueue.add( request );
								}
							}
						}
					} catch ( SocketException e ) {
						logger.info( "Socket was closed" );
						break;
					} catch ( IOException e ) {
						// TODO handle this exception
						e.printStackTrace();
						break;
					}
				}
			}

		} );

		this.inputThread.start();
	}

	/**
	 * Parse a debug request and deserialie it into its associated class.
	 * 
	 * @param json
	 * 
	 * @return
	 */
	private IDebugRequest parseDebugRequest( String json ) {
		Map<String, Object>	requestData	= ( Map<String, Object> ) JsonUtil.fromJson( json );
		String				command		= ( String ) requestData.get( "command" );

		this.logger.info( "Received command {}", command );
		this.logger.info( "Received command {}", json );

		// TODO move this into an instance of Map<String, Class>
		switch ( command ) {
			case "initialize" :
				return JsonUtil.fromJson( InitializeRequest.class, json );
			case "launch" :
				return JsonUtil.fromJson( LaunchRequest.class, json );
			case "setBreakpoints" :
				return JsonUtil.fromJson( SetBreakpointsRequest.class, json );
			case "configurationDone" :
				return JsonUtil.fromJson( ConfigurationDoneRequest.class, json );
			case "threads" :
				return JsonUtil.fromJson( ThreadsRequest.class, json );
			case "stackTrace" :
				return JsonUtil.fromJson( StackTraceRequest.class, json );
			case "scopes" :
				return JsonUtil.fromJson( ScopeRequest.class, json );
			case "variables" :
				return JsonUtil.fromJson( VariablesRequest.class, json );
			case "continue" :
				return JsonUtil.fromJson( ContinueRequest.class, json );
		}

		throw new NotImplementedException( command );
		// return null;
	}

	/**
	 * Default visit handler
	 * 
	 * @param debugRequest
	 */
	public void visit( IDebugRequest debugRequest ) {
		throw new NotImplementedException( debugRequest.getCommand() );
	}

	/**
	 * Visit InitializeRequest instances. Respond to the initialize request and send an initialized event.
	 * 
	 * @param debugRequest
	 */
	public void visit( InitializeRequest debugRequest ) {
		new InitializeResponse( debugRequest ).send( this.outputStream );
		new Event( "initialized" ).send( this.outputStream );
	}

	/**
	 * Visit InitializeRequest instances. Respond to the initialize request and send an initialized event.
	 * 
	 * @param debugRequest
	 */
	public void visit( ContinueRequest debugRequest ) {
		this.debugger.forceResume();
		new ContinueResponse( debugRequest, true ).send( this.outputStream );
	}

	/**
	 * Visit LaunchRequest instances. Send a NobodyResponse and setup a BoxLangDebugger.
	 * 
	 * @param debugRequest
	 */
	public void visit( LaunchRequest debugRequest ) {
		new NoBodyResponse( debugRequest ).send( this.outputStream );
		this.debugger = new BoxLangDebugger( BoxRunner.class, debugRequest.arguments.program, this.outputStream, this );
	}

	/**
	 * Visit SetBreakpointsRequest instances. Send a response.
	 * 
	 * @param debugRequest
	 */
	public void visit( SetBreakpointsRequest debugRequest ) {
		for ( Breakpoint bp : debugRequest.arguments.breakpoints ) {
			this.debugger.addBreakpoint( debugRequest.arguments.source.path, bp );
		}

		new SetBreakpointsResponse( debugRequest ).send( this.outputStream );
	}

	/**
	 * Visit ConfigurationDoneRequest instances. After responding the debugger can begin executing.
	 * 
	 * @param debugRequest
	 */
	public void visit( ConfigurationDoneRequest debugRequest ) {
		new NoBodyResponse( debugRequest ).send( this.outputStream );

		this.debugger.initialize();
	}

	/**
	 * Visit ThreadRequest instances. Should send a ThreadResponse contianing basic information about all vm threds.
	 * 
	 * @param debugRequest
	 */
	public void visit( ThreadsRequest debugRequest ) {
		List<ortus.boxlang.debugger.types.Thread> threads = this.debugger.getAllThreadReferences()
		    .stream()
		    .map( ( threadReference ) -> {
			    ortus.boxlang.debugger.types.Thread t = new ortus.boxlang.debugger.types.Thread();
			    t.id = ( int ) threadReference.uniqueID();
			    t.name = threadReference.name();

			    return t;
		    } )
		    .toList();

		try {

			new ThreadsResponse( debugRequest, threads ).send( this.outputStream );
		} catch ( Exception e ) {
			e.printStackTrace();
		}
	}

	/**
	 * Visit ThreadRequest instances. Should send a ThreadResponse contianing basic information about all vm threds.
	 * 
	 * @param debugRequest
	 */
	public void visit( StackTraceRequest debugRequest ) {
		try {
			// TODO convert from java info to boxlang info when possible
			// TODO decide if we should filter out java stack or make it available

			List<StackFrame> stackFrames = this.debugger.getStackFrames( debugRequest.arguments.threadId ).stream().map( ( stackFrame ) -> {
				StackFrame	sf	= new StackFrame();
				SourceMap	map	= javaBoxpiler.getSourceMapFromFQN( stackFrame.location().declaringType().name() );

				sf.id		= stackFrame.hashCode();
				sf.line		= stackFrame.location().lineNumber();
				sf.column	= 0;
				sf.name		= stackFrame.location().method().name();

				if ( map != null && map.isTemplate() ) {
					sf.name			= map.getFileName();
					sf.source		= new Source();
					sf.source.path	= map.source.toString();
					sf.source.name	= sf.name + "(Template)";
				}

				return sf;
			} )
			    .toList();

			new StackTraceResponse( debugRequest, stackFrames ).send( this.outputStream );
		} catch ( IncompatibleThreadStateException e ) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void visit( ScopeRequest debugRequest ) {
		com.sun.jdi.StackFrame vmStackFrame = findStackFrame( debugRequest.arguments.frameId );
		try {
			ObjectReference	context		= ( ObjectReference ) JDITools.findVariableyName( vmStackFrame, "context" );
			ObjectReference	variables	= ( ObjectReference ) JDITools.findPropertyByName( context, "variablesScope" );

			this.seenScopes.put( variables.hashCode(), new ScopeCache( vmStackFrame, variables ) );

			Scope variablesScope = new Scope();
			variablesScope.name					= "Variables Scope";
			variablesScope.variablesReference	= variables.hashCode();
			List<Scope> scopes = new ArrayList<Scope>();
			scopes.add( variablesScope );
			new ScopeResponse( debugRequest, scopes ).send( this.outputStream );
		} catch ( Exception e ) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void visit( VariablesRequest debugRequest ) {
		List<Variable> ideVars = new ArrayList<Variable>();

		if ( this.seenScopes.containsKey( debugRequest.arguments.variablesReference ) ) {
			ideVars = JDITools.gerVariablesFromStruct( this.seenScopes.get( debugRequest.arguments.variablesReference ).scope );
		} else if ( JDITools.hasSeen( debugRequest.arguments.variablesReference ) ) {
			ideVars = JDITools.gerVariablesFromStruct( ( ObjectReference ) JDITools.getSeenValue( debugRequest.arguments.variablesReference ) );
		}

		new VariablesResponse( debugRequest, ideVars ).send( this.outputStream );
	}

	private com.sun.jdi.StackFrame findStackFrame( int id ) {
		for ( com.sun.jdi.ThreadReference thread : this.debugger.getAllThreadReferences() ) {
			try {
				for ( com.sun.jdi.StackFrame stackFrame : this.debugger.getStackFrames( thread.hashCode() ) ) {
					if ( stackFrame.hashCode() == id ) {
						return stackFrame;
					}
				}
			} catch ( IncompatibleThreadStateException e ) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		return null;
	}

	// ===================================================
	// ================= EVENTS ==========================
	// ===================================================

	public void sendStoppedEventForBreakpoint( int threadId ) {
		StoppedEvent.breakpoint( threadId ).send( this.outputStream );
	}

	record ScopeCache( com.sun.jdi.StackFrame stackFrame, ObjectReference scope ) {

	};
}
