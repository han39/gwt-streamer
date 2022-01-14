package eu.dedalus.gwtstreamer.client.impl;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.stream.Stream;
import eu.dedalus.gwtstreamer.client.StreamFactory;
import eu.dedalus.gwtstreamer.client.Streamer;
import eu.dedalus.gwtstreamer.client.StreamerException;

public final class WriteContext extends Context implements StreamFactory.Writer {
	private static final String[] COLLECTIONS = { "java.util.ArrayList", "java.util.Collection", "java.util.Comparator", "java.util.HashMap", "java.util.HashSet", "java.util.IdentityHashMap",
			"java.util.LinkedHashMap", "java.util.LinkedHashSet", "java.util.LinkedList", "java.util.Map", "java.util.TreeMap", "java.util.TreeSet", "java.util.Vector" };

	private Map<Object, Integer> refs = new IdentityHashMap<>(30);
	private Map<Object, Integer> refsImm = new HashMap<>(70);

	private final StreamFactory.Writer out;

	public WriteContext(final StreamFactory.Writer out) {
		this.out = out;
	}

	public WriteContext(final StreamFactory.Writer out, final WriteContext init) {
		this(out);
		refs.putAll(init.refs);
		refsImm.putAll(init.refsImm);
		classNameStrings.addAll(init.classNameStrings);
	}

	/**
	 * Register object within context. In subsequent serializations an object's identity will be written instead of object data.
	 *
	 * @param obj
	 *           object instance to register
	 * @return object identity
	 * @throws IllegalStateException
	 *            if object is already registered within the context
	 */
	@Override
	public Integer addObject(final Object obj) {
		final Integer idx = Integer.valueOf(refs.size() + refsImm.size());
		Integer old;

		final String name = obj.getClass().getName();
		if (name.startsWith("java.") && !Stream.of(COLLECTIONS).anyMatch(c -> c.equals(name))) {
			old = refsImm.put(obj, idx);
		} else {
			old = refs.put(obj, idx);
		}

		if (old != null) {
			refs.put(obj, old);
			throw new IllegalStateException("Object identity already created. " +
					"Possible protocol mismatch. Please review custom streamers implementation. Class: "
					+ obj.getClass().getName());
		}

		return idx;
	}

	/**
	 * Get data serialized to String.
	 *
	 * @return serialized data.
	 */
	@Override
	public String getData() {
		return out.getData();
	}

	@Override
	public void writeBoolean(final boolean val) {
		out.writeBoolean(val);
	}

	@Override
	public void writeByte(final byte val) {
		out.writeByte(val);
	}

	@Override
	public void writeChar(final char val) {
		out.writeChar(val);
	}

	@Override
	public void writeDouble(final double val) {
		out.writeDouble(val);
	}

	@Override
	public void writeFloat(final float val) {
		out.writeFloat(val);
	}

	@Override
	public void writeInt(final int val) {
		out.writeInt(val);
	}

	@Override
	public void writeLong(final long val) {
		out.writeLong(val);
	}

	public void writeObject(final Object obj) {
		if (obj == null) {
			// write null (empty string)
			writePacked(NULL, 0);
		} else {
			final Integer refIdx = getObjectIdentity(obj);

			if (refIdx != null) {
				// write object reference (digits)
				writePacked(REF, refIdx);
			} else {
				// try to obtain class short name or reference
				final Class<?> clazz = obj.getClass();
				final String className = clazz.getName();

				if (!className.startsWith("[")) {
					// regular class
					final Integer classRef = writeClassName(className);
					writePacked(CLASS_REF, classRef);
				} else {
					// array
					int dim = 1;
					while (className.charAt(dim) == '[') {
						dim++;
					}

					if (className.charAt(dim) == 'L') {
						// array of objects
						final String objectClassName = className.substring(dim + 1, className.length() - 1);
						final Integer classRef = writeClassName(objectClassName);
						writePacked(OBJ_ARRAY_REF, dim);
						out.writeInt(classRef);
					} else {
						// array of primitives
						final char elem = className.charAt(dim);
						final int elemId = PRIMITIVES.indexOf(elem);

						if (dim <= 3) {
							// pack dimension & type
							final int val = elemId | dim - 1 << 3;
							writePacked(ARRAY_REF, val);
						} else {
							// pack type and write dimension apart
							final int val = elemId | 0x3 << 3;
							writePacked(ARRAY_REF, val);
							out.writeInt(dim);
						}
					}
				}

				final Streamer streamer = Streamer.get(clazz);

				if (streamer == null) {
					throw new StreamerException("Object of this class can not be serialized: " +
							clazz.getName());
				}

				streamer.writeObject(obj, this);
			}
		}

	}

	@Override
	public void writeShort(final short val) {
		out.writeShort(val);
	}

	@Override
	public void writeString(final String val) {
		out.writeString(val);
	}

	/**
	 * Get identity of serialized object
	 *
	 * @param obj
	 *           registered object
	 * @return object identity or null if object was not serialized before
	 */
	private Integer getObjectIdentity(final Object obj) {
		if (obj.getClass().getName().startsWith("java.")) {
			// java core classes are immutable - put them to normal hashMap
			return refsImm.get(obj);
		} else {
			return refs.get(obj);
		}
	}

	private Integer writeClassName(final String className) {
		Integer ref = getObjectIdentity(className);

		if (ref == null) {
			// eu.dedalus.gwtstreamer.shared
			// find largest prefix of streamed class
			String closestClassName = null;
			{
				// divide subpackages/classes into lines
				final String[] pp = className.split("[\\.\\$]");
				final StringBuilder sb = new StringBuilder(className.length());
				final String[] names = new String[pp.length];

				for (int i = 0 ; i < pp.length ; i++) {
					final String s = pp[i];
					sb.append(s);
					final String sRef = sb.toString();
					names[pp.length - i - 1] = sRef;
					if (i < pp.length - 1) {
						final char delim = className.charAt(sb.length());
						sb.append(delim);
					}
				}

				// find largest prefix
				int n;
				for (n = 1 ; n < names.length ; n++) {
					final String s = names[n];
					if (classNameStrings.contains(s)) {
						closestClassName = s;
						break;
					}
				}

				// add new packages/classes prefixes
				for (int i = n - 1 ; i >= 0 ; i--) {
					final String s = names[i];
					ref = addObject(s); // last written ID
					classNameStrings.add(s);
				}
			}

			if (closestClassName != null) {
				final Integer closestClassNameRef = getObjectIdentity(closestClassName);
				writePacked(STR_REF, closestClassNameRef);
				// .test.TestClass$MyClass | wordpart.TestClass
				final String restString = className.substring(closestClassName.length());
				out.writeString(restString);
			} else {
				writePacked(STR_DEF, 0);
				out.writeString(className);
			}
		}

		return ref;
	}

	private void writePacked(final int tag, int val) {
		// 0x1F = 00011111, 0x7=00000111
		// if unsigned(val) >= 32 (has more that lowest 5 bits equals to 00011111)
		if ((val & ~0x1F) != 0 && val != 0x1F) {
			// writing unpacked value:
			// lowest 3 bits = tag, highest 5 bits = 00000
			out.writeByte((byte) tag);
			out.writeInt(val);
		} else {
			// packed value
			// lowest 3 bits = tag, highest 5 bits = val
			val++;
			out.writeByte((byte) (val << 3 | tag));
		}
	}
}
