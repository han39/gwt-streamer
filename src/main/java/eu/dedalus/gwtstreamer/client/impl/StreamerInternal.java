package eu.dedalus.gwtstreamer.client.impl;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import eu.dedalus.gwtstreamer.client.Streamable;
import eu.dedalus.gwtstreamer.client.Streamer;
import eu.dedalus.gwtstreamer.client.StreamerException;
import eu.dedalus.gwtstreamer.client.std.ArrayStreamer;
import eu.dedalus.gwtstreamer.client.std.EnumStreamer;
import eu.dedalus.gwtstreamer.client.std.StructStreamer;

/**
 * This is a server-side implementation of the class. Client implementation is located in emu package.
 */
public class StreamerInternal {
	/**
	 * Initially configured GWT streamers class name -> class streamer Do not touch this field!!!
	 */
	public static Map<String, Streamer> INITIAL_STREAMERS = Collections.unmodifiableMap(Collections.<String, Streamer> emptyMap());

	/** Restriction policy derived from StreamerConfig */
	public static String classRestrictionPolicy;

	private static Streamer rootStreamer = new Streamer() {};
	private static ConcurrentHashMap<String, Streamer> streamers = new ConcurrentHashMap<>();

	/** ConcurrentHashMap does not allows NULL values */
	private final static Streamer NULL = new Streamer() {};

	/**
	 * Dynamically creates streamers for unknown types.
	 *
	 * @param className
	 * @return
	 */
	public static Streamer createStreamerFor(final String className) {
		Streamer st = streamers.get(className);

		if (st != null) {
			return st == NULL ? null : st;
		}

		final Class<?> clazz;

		try {
			checkClassNamePolicy(className);
			clazz = Class.forName(className);
		} catch (final Exception ex) {
			// return null;
			throw new StreamerException("Class not found: " + className, ex);
		}

		if (clazz.isInterface()) {
			st = null;
		} else if (clazz.isArray()) {
			st = createArrayStreamerFor(clazz);
		} else if (clazz.isEnum()) {
			st = createEnumStreamerFor(clazz);
		} else {
			if (!Streamable.class.isAssignableFrom(clazz)) {
				st = null;
			} else {
				st = createStructStreamerFor(clazz);
			}
		}

		if (st == null) {
			st = NULL;
		}

		final Streamer st1 = streamers.putIfAbsent(className, st);

		// we already have streamer in cache: return original value
		if (st1 != null) {
			st = st1;
		}

		return st == NULL ? null : st;
	}

	public static Streamer getRootStreamer() {
		return rootStreamer;
	}

	public static boolean isGWT() {
		return false;
	}

	public static void log(final String message) {
		System.out.println(message);
	}

	public static String urlDecode(final String s) {
		try {
			return URLDecoder.decode(s, "UTF-8");
		} catch (final Exception ex) {
			throw new StreamerException(ex);
		}
	}

	public static String urlEncode(final String s) {
		try {
			return URLEncoder.encode(s, "UTF-8");
		} catch (final Exception ex) {
			throw new StreamerException(ex);
		}
	}

	private static void checkClassNamePolicy(final String className) {
		if (!className.startsWith("[")) {
			// regular class
			if (classRestrictionPolicy != null) {
				if (!className.matches(classRestrictionPolicy)) {
					throw new StreamerException("Class name does not match to restriction policy: " + className);
				}
			}
		} else {
			// array
			int dim = 1;
			while (className.charAt(dim) == '[') {
				dim++;
			}

			if (dim > 256) {
				throw new StreamerException("Array dimension limit is 256. Requested dimensions: " + dim);
			}

			if (className.charAt(dim) == 'L') {
				// array of objects
				final String objectClassName = className.substring(dim + 1, className.length() - 1);
				checkClassNamePolicy(objectClassName);
			} else {
				// array of primitives
			}
		}
	}

	private static Streamer createArrayStreamerFor(final Class<?> clazz) {
		return new ArrayStreamer() {
			@Override
			protected Object[] createObjectArrayInstance(final int length) {
				return (Object[]) Array.newInstance(clazz.getComponentType(), length);
			}
		};
	}

	private static Streamer createEnumStreamerFor(final Class<?> clazz) {
		return new EnumStreamer() {
			private final Enum<?>[] values;
			{
				try {
					final Method m = clazz.getMethod("values");
					values = (Enum<?>[]) m.invoke(null);
				} catch (final Exception ex) {
					throw new StreamerException("Enum class not found", ex);
				}
			}

			@Override
			protected Enum<?> getEnumValueOf(final int value) {
				return values[value];
			}
		};
	}

	private static Streamer createStructStreamerFor(final Class<?> clazz) {
		return new StructStreamer() {
			private final SortedMap<String, Field> fields;
			private final Constructor<?> init;
			{
				try {
					// search for default constructor
					if ((clazz.getModifiers() & Modifier.ABSTRACT) == 0) {
						// instantiable
						Constructor<?> ini = null;
						try {
							ini = clazz.getConstructor();
						} catch (final Exception ex) {
							try {
								ini = clazz.getDeclaredConstructor();
							} catch (final Exception ex1) {
								// throw new StreamerException( "Class has no no-arg constructor: "+clazz.getName() );
							}
						}
						init = ini;
						if (init != null) {
							init.setAccessible(true);
						}
					} else {
						// abstract
						init = null;
					}

					// search for fields
					fields = new TreeMap<>();

					{ // if superclass is non-streamable search for fields in all superclasses
						Class<?> clType = clazz;

						do {
							final Field[] ff = clType.getDeclaredFields();

							for (final Field f : ff) {
								// if not static and not transient
								if ((f.getModifiers() & (Modifier.STATIC | Modifier.TRANSIENT)) == 0) {
									fields.put(f.getDeclaringClass().getName() + "::" + f.getName(), f);
									f.setAccessible(true);
								}
							}

							clType = clType.getSuperclass();
						} while (clType != null && !Streamable.class.isAssignableFrom(clType));
					}
				} catch (final Exception ex) {
					throw new StreamerException("Error creating streamer for class " + clazz.getName(), ex);
				}
			}

			@Override
			protected Object createObjectInstance() {
				if (init != null) {
					try {
						return init.newInstance();
					} catch (final Exception ex) {
						throw new StreamerException("Error instantiating class: " + clazz.getName());
					}
				} else {
					throw new StreamerException("Class can not be instantiated (it is abstract or has no no-arg constructor): " + clazz.getName());
				}
			}

			@Override
			protected int getFieldNum() {
				return fields.size();
			}

			@Override
			protected Class<?> getTargetClass() {
				return clazz;
			}

			@Override
			protected List<Object> getValues(final Object obj) {
				final List<Object> l = new ArrayList<>(fields.size());

				for (final Field f : fields.values()) {
					try {
						l.add(f.get(obj));
					} catch (final Exception ex) {
						throw new StreamerException("Could not access field " + f.getName() + " in class: " + clazz.getName());
					}
				}

				return l;
			}

			@Override
			protected void setValues(final Object obj, final List<Object> values) {
				int n = 0;
				for (final Field f : fields.values()) {
					try {
						f.set(obj, values.get(n));
					} catch (final Exception ex) {
						throw new StreamerException("Could not access field " + f.getName() + " in class: " + clazz.getName());
					}
					n++;
				}
			}
		};
	}
}
