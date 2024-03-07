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
package ortus.boxlang.runtime.cache.providers;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ortus.boxlang.runtime.cache.BoxCacheEntry;
import ortus.boxlang.runtime.cache.ICacheEntry;
import ortus.boxlang.runtime.cache.filters.ICacheKeyFilter;
import ortus.boxlang.runtime.cache.store.IObjectStore;
import ortus.boxlang.runtime.cache.util.BoxCacheStats;
import ortus.boxlang.runtime.config.segments.CacheConfig;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.services.CacheService;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

/**
 * The BoxCacheProvider class is a cache provider for BoxLang that
 * can use a variety of object stores to store cache data:
 *
 * - ConcurrentHashMap
 * - ConcurrentSoftReference
 * - Disk
 * - Custom
 *
 */
public class BoxCacheProvider extends AbstractCacheProvider {

	/**
	 * --------------------------------------------------------------------------
	 * Private Properties
	 * --------------------------------------------------------------------------
	 */

	/**
	 * Logger
	 */
	private static final Logger	logger	= LoggerFactory.getLogger( BoxCacheProvider.class );

	/**
	 * The object store we will use for caching
	 */
	private IObjectStore		objectStore;

	/**
	 * --------------------------------------------------------------------------
	 * Constructor
	 * --------------------------------------------------------------------------
	 */
	public BoxCacheProvider() {
		// empty
	}

	/**
	 * --------------------------------------------------------------------------
	 * Interface Methods
	 * --------------------------------------------------------------------------
	 */

	/**
	 * Configure the cache provider for operation
	 *
	 * @param cacheService The cache service that is configuring the cache provider
	 * @param config       The configuration object
	 *
	 * @return The cache provider
	 */
	@Override
	public synchronized ICacheProvider configure( CacheService cacheService, CacheConfig config ) {
		// Super size me!
		super.configure( cacheService, config );

		// Log it
		logger.atDebug().log(
		    "Starting up BoxCache [{}] with config [{}]",
		    getName().getName(),
		    config.toStruct()
		);

		// Create the stats
		this.stats				= new BoxCacheStats();
		// Create the object store and initialize it
		this.objectStore		= buildObjectStore( config ).init( this, config.properties );
		// Enable reporting
		this.reportingEnabled	= true;
		// Create the reaping scheduled task using the CacheService executor
		this.cacheService.getTaskScheduler()
		    // Get a new task
		    .newTask( "boxcache-reaper-" + getName().getName() )
		    // Don't start immediately, wait for the first reaping
		    .delay( config.properties.getAsLong( Key.reapFrequency ), TimeUnit.SECONDS )
		    // Reap every x seconds according to the config
		    .spacedDelay( config.properties.getAsLong( Key.reapFrequency ), TimeUnit.SECONDS )
		    // Register the reaper
		    .call( () -> reap() )
		    // Fire away!
		    .start();

		// We are ready to roll
		this.enabled.set( true );

		// Startup log
		logger.atInfo().log(
		    "BoxCache [{}] has been initialized and ready for operation",
		    getName().getName()
		);

		return this;
	}

	/**
	 * Return the configured object store for this cache provider
	 */
	public IObjectStore getObjectStore() {
		return this.objectStore;
	}

	/**
	 * Shutdown the cache provider
	 */
	public void shutdown() {
		this.objectStore.shutdown();
		logger.atInfo().log( "BoxCache [{}] has been shutdown", getName().getName() );
	}

	/**
	 * Get a structure of all the keys in the cache with their appropriate metadata structures.
	 * This is used to build the reporting for the cache provider
	 * Example:
	 *
	 * <pre>
	 * {
	 *    "key1": {
	 * 	  "hits": 0,
	 * 	  "lastAccessed": 0,
	 * 	  "lastUpdated": 0,
	 * 	   ...
	 *   },
	 *  "key2": {
	 * 	  "hits": 0,
	 * 	  "lastAccessed": 0,
	 * 	  "lastUpdated": 0,
	 * 	  ...
	 *  }
	 * }
	 * </pre>
	 *
	 * The {@code getStoreMetadataKeyMap} method is used to get the keys that
	 * this method returns as metadata in order to build the reports.
	 *
	 * Careful, this will be a large structure if the cache is large.
	 */
	public IStruct getStoreMetadataReport( int limit ) {
		IStruct report = new Struct();
		this.objectStore.getKeysStream()
		    .limit( limit )
		    .forEach( key -> report.put( key, this.objectStore.getQuiet( key ).toStruct() ) );
		return report;
	}

	/**
	 * Get a key lookup structure where the BoxCache can build the report on.
	 * Ex: {@code { timeout=timeout, lastAccessTimeout=idleTimeout }
	 * It is a way for the visualizer to construct the columns correctly on the reports
	 */
	public IStruct getStoreMetadataKeyMap() {
		return Struct.of(
		    "cacheName", "cacheName",
		    "hits", "hits",
		    "timeout", "timeout",
		    "lastAccessTimeout", "lastAccessTimeout",
		    "created", "created",
		    "lastAccessed", "lastAccessed",
		    "metadata", "metadata",
		    "key", "key",
		    "isEternal", "isEternal"
		);
	}

	/**
	 * Get a cache objects metadata about its performance. This value is a structure of name-value pairs of metadata.
	 *
	 * @param key The key of the object
	 *
	 * @return The metadata structure or an empty struct if the object is not found
	 */
	public IStruct getCachedObjectMetadata( String key ) {
		var results = this.objectStore.get( Key.of( key ) );
		return results != null ? results.toStruct() : new Struct();
	}

	/**
	 * Reap the cache
	 */
	public synchronized void reap() {
		// Start a timer
		long start = System.currentTimeMillis();

		// Log start
		logger.atDebug().log(
		    "Reaping BoxCache [{}]...",
		    getName().getName()
		);

		// Run an object store eviction first
		this.objectStore.evict();

		// Now do expiration checks
		Instant rightNow = Instant.now();
		this.objectStore
		    .getKeysStream()
		    // Map to the ICacheEntry
		    .map( this.objectStore::getQuiet )
		    // Filter out nulls
		    .filter( Objects::nonNull )
		    // Only non-eternal objects
		    .filter( entry -> !entry.isEternal() )
		    // Operate on it
		    .forEach( entry -> {

			    // Check if the creation + timeout is before now
			    if ( entry.created().plusSeconds( entry.timeout() ).isBefore( rightNow ) ) {
				    clear( entry.key().getName() );
				    return;
			    }

			    // Last Access Timeout
			    if ( config.properties.getAsBoolean( Key.useLastAccessTimeouts ) &&
			        entry.lastAccessTimeout() > 0 &&
			        entry.lastAccessed().plusSeconds( entry.lastAccessTimeout() ).isBefore( rightNow ) ) {
				    clear( entry.key().getName() );
			    }

		    } );

		// Record it
		getStats().recordReap();

		// Log it
		logger.atDebug().log(
		    "Finished reaping BoxCache [{}] in [{}]ms",
		    getName().getName(),
		    System.currentTimeMillis() - start
		);
	}

	/**
	 * Get the size of the cache
	 */
	public int getSize() {
		return this.objectStore.getSize();
	}

	/**
	 * Clear all the elements in the cache provider
	 */
	public void clearAll() {
		this.objectStore.clear();
		// Announce it
		announce(
		    CacheService.CACHE_EVENTS.get( "afterCacheClearAll" ),
		    Struct.of( "cache", this )
		);
	}

	/**
	 * Clear all the elements in the cache provider with a ${@link ICacheKeyFilter} predicate.
	 * This can be a lambda or method reference since it's a functional interface.
	 *
	 * @param filter The filter that determines which keys to clear
	 */
	public void clearAll( ICacheKeyFilter filter ) {
		this.objectStore.clearAll( filter );
		// Announce it
		announce(
		    CacheService.CACHE_EVENTS.get( "afterCacheClearAll" ),
		    Struct.of( "cache", this, "filter", filter )
		);
	}

	/**
	 * Clears an object from the cache provider
	 *
	 * @param key The object key to clear
	 *
	 * @return True if the object was cleared, false otherwise (if the object was not found in the store)
	 */
	public boolean clearQuiet( String key ) {
		return this.objectStore.clear( Key.of( key ) );
	}

	/**
	 * Clears an object from the cache provider
	 *
	 * @param key The object key to clear
	 *
	 * @return True if the object was cleared, false otherwise (if the object was not found in the store)
	 */
	public boolean clear( String key ) {
		boolean cleared = clearQuiet( key );

		// Announce it
		announce(
		    CacheService.CACHE_EVENTS.get( "afterCacheElementRemoved" ),
		    Struct.of( "cache", this, "key", key, "cleared", cleared )
		);

		return cleared;
	}

	/**
	 * Clears multiple objects from the cache provider
	 *
	 * @param keys The keys to clear
	 *
	 * @return A struct of keys and their clear status
	 */
	public IStruct clear( String... keys ) {
		IStruct cleared = new Struct();
		for ( String key : keys ) {
			cleared.put( key, clear( key ) );
		}
		return cleared;
	}

	/**
	 * Get all the keys in the cache provider
	 *
	 * @return An array of keys in the cache
	 */
	public String[] getKeys() {
		return this.objectStore
		    .getKeysStream()
		    .map( Key::getName )
		    .toArray( String[]::new );
	}

	/**
	 * Get all the keys in the cache provider using a filter
	 *
	 * @param filter The filter that determines which keys to return
	 *
	 * @return An array of keys in the cache
	 */
	public String[] getKeys( ICacheKeyFilter filter ) {
		return this.objectStore
		    .getKeysStream()
		    .filter( filter )
		    .map( Key::getName )
		    .toArray( String[]::new );
	}

	/**
	 * Get all the keys in the cache provider as a stream
	 *
	 * @return A stream of keys in the cache
	 */
	public Stream<String> getKeysStream() {
		return this.objectStore
		    .getKeysStream()
		    .map( Key::getName );
	}

	/**
	 * Get all the keys in the cache provider as a stream
	 *
	 * @param filter The filter that determines which keys to return
	 *
	 * @return A stream of keys in the cache
	 */
	public Stream<String> getKeysStream( ICacheKeyFilter filter ) {
		return this.objectStore
		    .getKeysStream()
		    .filter( filter )
		    .map( Key::getName );
	}

	/**
	 * Check if an object is in the store with no stats updated or listeners
	 *
	 * @param key The key to lookup in the store
	 *
	 * @return True if the object is in the store, false otherwise
	 */
	public boolean lookupQuiet( String key ) {
		return this.objectStore.lookup( Key.of( key ) );
	}

	/**
	 * Check if an object is in the store or record a hit or miss in the stats
	 *
	 * @param key The key to lookup in the store
	 *
	 * @return True if the object is in the store, false otherwise
	 */
	public boolean lookup( String key ) {
		boolean found = lookupQuiet( key );

		// Stats
		if ( found ) {
			this.stats.recordHit();
		} else {
			this.stats.recordMiss();
		}

		return found;
	}

	/**
	 * Check if multiple objects are in the store
	 *
	 * @param key A varargs of keys to lookup in the store
	 *
	 * @return A struct of keys and their lookup status
	 */
	public IStruct lookup( String... keys ) {
		IStruct found = new Struct();
		for ( String key : keys ) {
			found.put( key, lookup( key ) );
		}
		return found;
	}

	/**
	 * Check if multiple objects are in the store using a filter
	 *
	 * @param filter The filter that determines which keys to return
	 *
	 * @return A struct of keys and their lookup status
	 */
	public IStruct lookup( ICacheKeyFilter filter ) {
		IStruct found = new Struct();
		this.objectStore
		    .getKeysStream()
		    .filter( filter )
		    .forEach( key -> found.put( key.getName(), lookup( key.getName() ) ) );
		return found;
	}

	/**
	 * Get an object from cache with no metadata tracking
	 *
	 * @param key The key to retrieve
	 *
	 * @return The cache entry retrieved or null
	 */
	public Optional<Object> getQuiet( String key ) {
		return Optional.ofNullable(
		    this.objectStore.getQuiet( Key.of( key ) )
		);
	}

	/**
	 * Get an object from the store with metadata tracking
	 *
	 * @param key The key to retrieve
	 *
	 * @return The value retrieved or null
	 */
	public Optional<Object> get( String key ) {
		// Get it like a ninja
		var results = getQuiet( key );

		// Record the hit or miss
		if ( results.isPresent() ) {
			this.stats.recordHit();
		} else {
			this.stats.recordMiss();
		}

		// Run eviction checks async using a CompletableFuture
		getTaskScheduler().submit( this::evictChecks );

		return results;
	}

	/**
	 * Get multiple objects from the store with metadata tracking
	 *
	 * @param key The keys to retrieve
	 *
	 * @return A struct of keys and their cache entries
	 */
	public IStruct get( String... keys ) {
		IStruct results = new Struct();
		for ( String key : keys ) {
			results.put( key, get( key ) );
		}
		return results;
	}

	/**
	 * Get multiple objects from the store with metadata tracking using a filter
	 *
	 * @param filter The filter that determines which keys to return
	 *
	 * @return A struct of keys and their cache entries
	 */
	public IStruct get( ICacheKeyFilter filter ) {
		IStruct results = new Struct();
		this.objectStore
		    .getKeysStream()
		    .filter( filter )
		    .forEach( key -> results.put( key.getName(), get( key.getName() ) ) );
		return results;
	}

	/**
	 * Sets an object in the storage with no announcements or eviction checks
	 *
	 * @param key   The key to store
	 * @param value The value to store
	 */
	public void setQuiet( Key key, ICacheEntry value ) {
		this.objectStore.set( key, value );
	}

	/**
	 * Sets an object in the storage
	 *
	 * @param key               The key to store
	 * @param value             The value to store
	 * @param timeout           The timeout in seconds
	 * @param lastAccessTimeout The last access timeout in seconds
	 * @param metadata          The metadata to store
	 */
	public void set( String key, Object value, Duration timeout, Duration lastAccessTimeout, IStruct metadata ) {
		// Check if updating or not
		var	oldEntry	= getQuiet( key );

		// Prep new entry
		var	boxKey		= Key.of( key );
		var	newEntry	= new BoxCacheEntry(
		    getName(),
		    timeout.toSeconds(),
		    lastAccessTimeout.toSeconds(),
		    boxKey,
		    value,
		    metadata
		);

		// Run eviction checks async using a CompletableFuture
		getTaskScheduler().submit( this::evictChecks );

		// set the new object
		setQuiet( boxKey, newEntry );

		// Announce it
		if ( oldEntry.isPresent() ) {
			announce(
			    CacheService.CACHE_EVENTS.get( "afterCacheElementUpdated" ),
			    Struct.of(
			        "cache", this,
			        "key", boxKey,
			        "oldEntry", oldEntry,
			        "newEntry", newEntry
			    )
			);
		} else {
			announce(
			    CacheService.CACHE_EVENTS.get( "afterCacheElementInsert" ),
			    Struct.of(
			        "cache", this,
			        "key", boxKey,
			        "entry", newEntry
			    )
			);
		}
	}

	/**
	 * Runs the eviction checks against the cache provider rules
	 */
	private void evictChecks() {
		Boolean runEvict = false;

		// JVM Checks for eviction
		if ( memoryThresholdCheck() ) {
			runEvict = true;
		}

		// Max Objects Check
		if ( getSize() >= this.config.properties.getAsInteger( Key.maxObjects ) ) {
			runEvict = true;
		}

		// Run the eviction
		if ( runEvict ) {
			this.objectStore.evict();
		}
	}

}