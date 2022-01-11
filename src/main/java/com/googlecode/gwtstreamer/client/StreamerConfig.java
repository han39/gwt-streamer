package com.googlecode.gwtstreamer.client;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import com.googlecode.gwtstreamer.client.impl.Base64PackedStreamFactory;

/**
 * Object to apply custom streamer configuration
 * Created by anton on 23/01/2015.
 */
public class StreamerConfig {
    private SortedSet<String> registeredNames = new TreeSet<String>();
    private SortedMap<Class<?>,Streamer> registeredStreamers = new TreeMap<Class<?>,Streamer>();
    private StreamFactory streamFactory = new Base64PackedStreamFactory();
    private String classRestrictionPolicy;

    /**
     * Creates default configuration
     */
    public StreamerConfig() {}

    public StreamerConfig(SortedSet<String> registeredNames) {
        this.registeredNames = registeredNames;
    }

    public StreamerConfig(SortedSet<String> registeredNames, SortedMap<Class<?>, Streamer> registeredStreamers) {
        this.registeredNames = registeredNames;
        this.registeredStreamers = registeredStreamers;
    }

    public StreamerConfig(SortedSet<String> registeredNames, SortedMap<Class<?>, Streamer> registeredStreamers, StreamFactory streamFactory) {
        this.registeredNames = registeredNames;
        this.registeredStreamers = registeredStreamers;
        this.streamFactory = streamFactory;
    }

    /**
     * @return registered names (unmodifiable)
     */
    public Set<String> getRegisteredNames() {
        return Collections.unmodifiableSet(registeredNames);
    }

    /**
     * Register names
     * @param registeredNames a collection of names to register
     */
    public void registerNames(Collection<String> registeredNames) {
        if (registeredNames == null)
            throw new NullPointerException();
        this.registeredNames.addAll(registeredNames);
    }

    /**
     * @return registered custom streamers (unmodifiable)
     */
    public Map<Class<?>, Streamer> getRegisteredStreamers() {
        return Collections.unmodifiableMap(registeredStreamers);
    }

    /**
     * Register custom streamers
     * @param registeredStreamers a map of custom streamers to register
     */
    public void registerStreamers(Map<Class<?>, Streamer> registeredStreamers) {
        if (registeredStreamers == null)
            throw new NullPointerException();
        this.registeredStreamers.putAll(registeredStreamers);
    }

    /**
     * Register custom streamer
     * @param clazz class that requires custom serialization
     * @param streamer an instance of streamer implementation
     */
    public void registerStreamer(Class<?> clazz, Streamer streamer) {
        this.registeredStreamers.put(clazz, streamer);
    }

    public StreamFactory getStreamFactory() {
        return streamFactory;
    }

    /**
     * Set custom implementation of I/O streams
     * @param streamFactory
     */
    public void setStreamFactory(StreamFactory streamFactory) {
        if (streamFactory == null)
            throw new NullPointerException();
        this.streamFactory = streamFactory;
    }

    public void registerName( String name ) {
        this.registeredNames.add( name );
    }

    public void registerClass(Class<?> clazz) {
        this.registeredNames.add( clazz.getName() );
    }

    /**
     * Get current class restriction policy
     * @return regexp of allowed class names or null if policy is not set
     */
    public String getClassRestrictionPolicy() {
        return classRestrictionPolicy;
    }

    /**
     * Set class restriction policy to protect server side against protocol attacks that
     * try to inject names of classes out of business scope.
     * This only affects a server side and has no effect on client side.
     * It does not change config version number and not needed to be specified on client side.
     * Note! You do not need to add a standard classes like java.lang.String, java.util.ArrayList,
     * arrays, etc. to your regexp.
     * @param allowedClassesRegexp a regexp filter of allowed classes or null to disable
     *                               class name check
     */
    public void setClassRestrictionPolicy(String allowedClassesRegexp) {
        this.classRestrictionPolicy = allowedClassesRegexp;
    }


    /**
     * Return current version of configuration. A version number is a hash that depends of registered names,
     * streamers and streamFactory.
     * @return current version of configuration
     */
    public int getVersion() {
        int streamVersion = 0;
        for ( Map.Entry<Class<?>,Streamer> e : registeredStreamers.entrySet() )
            streamVersion ^= hashCode(e.getKey().getName()) ^ hashCode(e.getValue().getClass().getName());
        for ( String name : registeredNames )
            streamVersion ^= hashCode(name);
        if ( streamFactory != null )
            streamVersion ^= hashCode(streamFactory.getClass().getName());
        return streamVersion;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
		sb.append("Current stream version: ").append(getVersion()).append('\n');
		sb.append("Stream factory: ").append(streamFactory.getClass().getName()).append('\n');
		sb.append("Registered streamers:").append('\n');

        for ( Map.Entry<Class<?>,Streamer> e : registeredStreamers.entrySet() )
			sb.append("  ").append(e.getKey().getName()).append(" -> ").append(e.getClass().getName()).append('\n');

		sb.append("Registered names:").append('\n');

		for (String name : registeredNames)
			sb.append("  ").append(name).append('\n');
        return sb.toString();
    }


    /**
     * We do not rely on GWT String.hashCode() implementation as it may differ
     * from the JVM one.
     * @param s string
     * @return hash code
     */
    private static int hashCode(String s) {
        int h = 0;
        for (int i = 0; i < s.length(); i++) {
            int c = (int) s.charAt(i);
            h = 31 * h + c;
            h = ~(~h);		// JS overflow check
        }
        return h;
    }
}
