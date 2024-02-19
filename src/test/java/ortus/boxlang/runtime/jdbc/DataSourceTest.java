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
 * distributed under the License is distribu ted on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ortus.boxlang.runtime.jdbc;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Struct;

public class DataSourceTest {

	static DataSource datasource;

	@BeforeAll
	public static void setUp() {
		datasource = new DataSource( Struct.of(
		    "jdbcUrl", "jdbc:derby:memory:testDB;create=true"
		) );
		datasource.execute( "CREATE TABLE developers ( id INTEGER, name VARCHAR(155) )" );
		datasource.execute( "INSERT INTO developers ( id, name ) VALUES ( 1, 'Luis Majano' )" );
	}

	@AfterAll
	public static void teardown() throws SQLException {
		datasource.shutdown();
	}

	@DisplayName( "It can get an Apache Derby JDBC connection" )
	@Test
	void testDerbyConnection() throws SQLException {
		DataSource derbyDB = new DataSource( Struct.of(
		    Key.of( "jdbcUrl" ), "jdbc:derby:memory:funkyDB;create=true"
		) );
		try ( Connection conn = derbyDB.getConnection() ) {
			assertThat( conn ).isInstanceOf( Connection.class );
		}
		derbyDB.shutdown();
	}

	// @TODO: Move to mysql JDBC module tests?
	@Disabled( "Need ability to enable/disable tests based on available third-party services." )
	@DisplayName( "It can get a MySQL JDBC connection" )
	@Test
	void testMySQLConnection() throws SQLException {
		DataSource	myDatasource	= new DataSource( Struct.of(
		    // "driver", "mysql",
		    "username", "root",
		    "password", "secret",
		    "databaseName", "test",
		    "jdbcUrl", "jdbc:mysql://localhost:3306"
		) );
		Connection	conn			= myDatasource.getConnection();
		assertThat( conn ).isInstanceOf( Connection.class );
	}

	@Disabled( "Need to implement this!" )
	@DisplayName( "It can get a JDBC connection regardless of key casing" )
	@Test
	void testDerbyConnectionFunnyKeyCasing() throws SQLException {
		DataSource	funkyDatasource	= new DataSource( Struct.of(
		    "JDBCurl", "jdbc:derby:src/test/resources/tmp/testDB;create=true"
		) );
		Connection	conn			= funkyDatasource.getConnection();
		assertThat( conn ).isInstanceOf( Connection.class );
	}

	@DisplayName( "It closes datasource connections on shutdown" )
	@Test
	void testDatasourceClose() throws SQLException {
		DataSource	myDatasource	= new DataSource( Struct.of(
		    "username", "user",
		    "password", "password",
		    "jdbcUrl", "jdbc:derby:src/test/resources/tmp/testDB;create=true"
		) );
		Connection	conn			= myDatasource.getConnection();
		assertThat( conn ).isInstanceOf( Connection.class );

		myDatasource.shutdown();
		assertThat( conn.isValid( 5 ) ).isFalse();
	}

	@DisplayName( "It can execute simple queries without or without providing a connection" )
	@Test
	void testQueryExecute() {
		assertDoesNotThrow( () -> {
			datasource.execute( "INSERT INTO developers ( id, name ) VALUES ( 77, 'Michael Born' )" );
		} );
		try ( Connection conn = datasource.getConnection() ) {
			assertDoesNotThrow( () -> {
				datasource.execute( "SELECT * FROM developers", conn );
			} );
		} catch ( SQLException e ) {
			throw new RuntimeException( e );
		}
	}

	@DisplayName( "It can get results back from the query" )
	@Test
	void testQueryExecuteResults() {
		Struct[] results = datasource.execute( "SELECT * FROM developers WHERE id=1" );
		assertTrue( results.length > 0 );

		assert ( results[ 0 ].containsKey( "id" ) );
		assert ( results[ 0 ].containsKey( "name" ) );

		assert ( results[ 0 ].getAsInteger( Key.of( "id" ) ) == 1 );
		assert ( results[ 0 ].getAsString( Key.of( "name" ) ).equals( "Luis Majano" ) );
	}

	@DisplayName( "It can execute queries in a transaction, with or without providing a specific connection" )
	@Test
	void testTransactionalQueryExecute() throws SQLException {
		assertDoesNotThrow( () -> {
			datasource.executeTransactionally( new String[] {
			    "INSERT INTO developers (id) VALUES ( 11 )",
			    "INSERT INTO developers (id) VALUES ( 12 )"
			} );
		} );
		assertDoesNotThrow( () -> {
			datasource.executeTransactionally( new String[] {
			    "INSERT INTO developers (id) VALUES ( 13 )",
			    "INSERT INTO developers (id) VALUES ( 14 )"
			}, datasource.getConnection() );
		} );

		Connection conn = datasource.getConnection();
		datasource.executeTransactionally( new String[] {
		    "INSERT INTO developers (id) VALUES ( 15 )",
		    "INSERT INTO developers (id) VALUES ( 16 )"
		}, conn );

		// In the case where we pass in our own connection, it is up to us to close it.
		assertFalse( conn.isClosed(), "Caller-provided connection should NOT be closed on completion" );
	}
}