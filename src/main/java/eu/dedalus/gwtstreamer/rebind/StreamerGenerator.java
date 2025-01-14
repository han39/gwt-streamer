package eu.dedalus.gwtstreamer.rebind;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import com.google.gwt.core.ext.Generator;
import com.google.gwt.core.ext.GeneratorContext;
import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.UnableToCompleteException;
import com.google.gwt.core.ext.typeinfo.JArrayType;
import com.google.gwt.core.ext.typeinfo.JClassType;
import com.google.gwt.core.ext.typeinfo.JEnumType;
import com.google.gwt.core.ext.typeinfo.JField;
import com.google.gwt.core.ext.typeinfo.JParameterizedType;
import com.google.gwt.core.ext.typeinfo.JPrimitiveType;
import com.google.gwt.core.ext.typeinfo.JType;
import com.google.gwt.core.ext.typeinfo.TypeOracle;
import com.google.gwt.user.rebind.ClassSourceFileComposerFactory;
import com.google.gwt.user.rebind.SourceWriter;
import eu.dedalus.gwtstreamer.client.Streamable;
import eu.dedalus.gwtstreamer.client.StreamerConfig;
import eu.dedalus.gwtstreamer.client.StreamerException;
import eu.dedalus.gwtstreamer.client.impl.StreamerInternal;
import eu.dedalus.gwtstreamer.client.std.ArrayStreamer;
import eu.dedalus.gwtstreamer.client.std.EnumStreamer;
import eu.dedalus.gwtstreamer.client.std.StructStreamer;

public class StreamerGenerator extends Generator {

	private SourceWriter out;
	private TypeOracle typeOracle;

	@Override
	public String generate(final TreeLogger logger, final GeneratorContext ctx,
			final String requestedClass) throws UnableToCompleteException {
		// get the type oracle
		typeOracle = ctx.getTypeOracle();

		// get class from type oracle
		final JClassType streamerClass = typeOracle.findType(requestedClass);
		final JClassType streamableInterface = typeOracle.findType(Streamable.class.getName());

		if (streamerClass == null) {
			logger.log(TreeLogger.ERROR, "Unable to find metadata for type '"
					+ requestedClass + "'", null);
			throw new UnableToCompleteException();
		}

		// create source writer
		final String packageName = streamerClass.getPackage().getName();
		final String streamerImplClassName = streamerClass.getSimpleSourceName() + "_Impl";
		final PrintWriter printWriter = ctx.tryCreate(logger, packageName, streamerImplClassName);

		if (printWriter == null) {
			return packageName + "." + streamerImplClassName;
		}

		// Writing Streamer_Impl file
		final ClassSourceFileComposerFactory composerFactory = new ClassSourceFileComposerFactory(packageName, streamerImplClassName);
		composerFactory.setSuperclass("Streamer");

		// Java imports
		composerFactory.addImport(java.util.Collection.class.getName());
		composerFactory.addImport(java.util.Collections.class.getName());
		composerFactory.addImport(java.util.List.class.getName());
		composerFactory.addImport(java.util.ArrayList.class.getName());
		composerFactory.addImport(java.util.LinkedList.class.getName());
		composerFactory.addImport(java.util.Stack.class.getName());
		composerFactory.addImport(java.util.Vector.class.getName());
		composerFactory.addImport(java.util.Set.class.getName());
		composerFactory.addImport(java.util.TreeSet.class.getName());
		composerFactory.addImport(java.util.HashSet.class.getName());
		composerFactory.addImport(java.util.LinkedHashSet.class.getName());
		composerFactory.addImport(java.util.SortedSet.class.getName());
		composerFactory.addImport(java.util.Date.class.getName());
		composerFactory.addImport(java.util.Map.class.getName());
		composerFactory.addImport(java.util.HashMap.class.getName());
		composerFactory.addImport(java.util.LinkedHashMap.class.getName());
		composerFactory.addImport(java.util.TreeMap.class.getName());

		composerFactory.addImport(com.google.gwt.core.client.GWT.class.getName());
		composerFactory.addImport(StructStreamer.class.getName());
		composerFactory.addImport(StreamerInternal.class.getName());
		composerFactory.addImport(StreamerConfig.class.getName());
		composerFactory.addImport(StreamerException.class.getName());
		composerFactory.addImport(ArrayStreamer.class.getName());
		composerFactory.addImport(EnumStreamer.class.getName());
		composerFactory.addImport(Enum.class.getName());

		// Search for Streamable classes
		final Set<JClassType> streamableTypes = new TreeSet<>((o1, o2) -> {
			final String s1 = o1.getPackage().getName();
			final String s2 = o2.getPackage().getName();
			return s1.length() < s2.length() ? -1 : s1.length() > s2.length() ? 1 : signature(o1).compareTo(signature(o2));
		});

		{ // classes that implement Streamable
			final JClassType[] subTypes = streamableInterface.getSubtypes();
			streamableTypes.addAll(Arrays.asList(subTypes));
		}

		for (final JClassType type : streamableTypes) {
			composerFactory.addImport(type.getQualifiedSourceName());
		}

		out = composerFactory.createSourceWriter(ctx, printWriter);

		if (out == null) {
			return packageName + "." + streamerImplClassName;
		}

		out.println("static {");
		out.indent();
		out.println("Map<String,Streamer> initStreamers = new HashMap<String,Streamer>();");

		// types discovered during generation (arrays, enums)
		final Set<JType> discoveredTypes = new HashSet<>();

		final JClassType objectType = typeOracle.findType(java.lang.Object.class.getName());
		final JClassType stringType = typeOracle.findType(java.lang.String.class.getName());

		/*
		 * create a StructStreamer for each streamable type
		 */
		for (final JClassType type : streamableTypes) {
			if (type.isInterface() != null) {
				continue;
			}

			// ensure that non-abstract type has default no-arg constructor
			if (!type.isAbstract() && !type.isDefaultInstantiable()) {
				logger.log(TreeLogger.WARN, "Class '"
						+ type.getName() + "' extends Streamable but has no " +
						"no-arg constructor. Please create define a no-arg constructor " +
						"or declare the class as abstract.", null);
			}

			final SortedMap<String, JField> fields = new TreeMap<>();

			{ // if superclass is non-streamable search for fields in all superclasses
				JClassType clType = type;

				do {
					final JField[] ff = clType.getFields();

					for (final JField f : ff) {
						if (!f.isStatic() && !f.isTransient()) {
							fields.put(signature(f.getEnclosingType()) + "::" + f.getName(), f);
						}
					}

					clType = clType.getSuperclass();
				} while (clType != null && !clType.isAssignableTo(streamableInterface));
			}

			// discover types
			for (final JField f : fields.values()) {
				JArrayType at = f.getType().isArray();
				final JEnumType et = f.getType().isEnum();
				final JParameterizedType pt = f.getType().isParameterized();

				// JClassType ct = f.getType().isClassOrInterface();

				if (at != null) {
					// System.out.println( "Discovered array type: "+at.getQualifiedSourceName() );
					// array
					JType ct = at.getComponentType();
					JClassType cct = ct.isClassOrInterface();
					// System.out.println( " component type: "+at.getComponentType() );
					// System.out.println( " cct: "+cct );

					// add component type and all subcomponent types to discoverable
					while ( // we have a default implementation for all primitive arrays
					at != null && at.getComponentType().isPrimitive() == null
					// we also have default implementation for String[] and Object[]
							&& (cct == null || cct != null
									&& !cct.equals(objectType)
									&& !cct.equals(stringType))) {
						// System.out.println( " type added to discovery" );
						discoveredTypes.add(at);
						at = ct.isArray();

						if (at != null) {
							// component type is array
							ct = at.getComponentType();
							cct = ct.isClassOrInterface();
						} else {
							// component type is a class or enum
							if (!streamableTypes.contains(cct)) {
								discoveredTypes.add(cct);
							}
						}
					}
				} else if (et != null) {
					// enum
					discoveredTypes.add(et);
				} else if (pt != null) {
					// parameterized type (collection)
					discoverParameterizedEnums(discoveredTypes, pt);
				}
			}

			out.println("initStreamers.put( \"" + signature(type) + "\", new StructStreamer() {");
			out.indent();
			out.println(
					"@Override protected int getFieldNum() { return " + fields.size() + "; }");
			out.println(
					"@Override protected Class<?> getTargetClass() { return "
							+ type.getQualifiedSourceName() + ".class; }");

			if (!type.isAbstract() && type.isDefaultInstantiable()) {
				out.println("@Override protected native Object createObjectInstance() /*-{");
				out.indent();
				out.println("return @" + type.getQualifiedSourceName() + "::new()();");
				out.outdent();
				out.println("}-*/;");
			} else {
				out.println("@Override protected Object createObjectInstance() {");
				out.indent();
				out.println("throw new StreamerException(\"Class can not be instantiated (is abstract " +
						"or has no no-arg constructor): \"+getTargetClass().getName() );");
				out.outdent();
				out.println("}");
			}

			out.println("@com.google.gwt.core.client.UnsafeNativeLong");
			out.println("@Override protected native List<Object> getValues( Object obj ) /*-{");
			out.indent();
			out.println("var values = @java.util.ArrayList::new()();");
			int n = 0;
			for (final JField f : fields.values()) {
				writeGetField(out, f);
				n++;
			}
			out.println("return values;");
			out.outdent();
			out.println("}-*/;");

			out.println("@com.google.gwt.core.client.UnsafeNativeLong");
			out.println("@Override protected native void setValues( Object obj, List<Object> values ) /*-{");
			out.indent();
			n = 0;
			for (final JField f : fields.values()) {
				writeSetField(out, f, n);
				// out.println( "obj.@"+type.getQualifiedSourceName()+"::"+f.getName()+" = values["+n+"];" );
				n++;
			}
			out.outdent();
			out.println("}-*/;");

			out.outdent();
			out.println("} );");
			out.println();
		}

		// discovered types serialization (arrays, enums)
		for (final JType type : discoveredTypes) {
			final JArrayType at = type.isArray();
			final JEnumType et = type.isEnum();

			if (at != null) {
				out.println("initStreamers.put( \"" + signature(type) + "\", new ArrayStreamer() {");
				out.indent();
				out.println(
						"@Override protected Object[] createObjectArrayInstance( int length ) {");
				String init = type.getQualifiedSourceName();
				final int iArray = init.indexOf('[');
				init = init.substring(0, iArray + 1) + "length" + init.substring(iArray + 1);
				out.println("  return new " + init + ";");
				out.println("}");
				out.outdent();
				out.println("} );");
				out.println();
			} else {
				// class or enum
				composerFactory.addImport(type.getQualifiedSourceName());

				if (et != null) {
					// enum
					out.println("initStreamers.put( \"" + signature(type) + "\", new EnumStreamer() {");
					out.indent();
					out.println(
							"@Override protected Enum<?> getEnumValueOf( int value ) {");
					out.println("    return " + type.getQualifiedSourceName() + ".values()[value];");
					out.println("}");
					out.outdent();
					out.println("} );");
					out.println();
				} else {
					// class must be Streamable or class Streamer must be set explicitly
				}
			}
		}

		out.println("StreamerInternal.INITIAL_STREAMERS = Collections.unmodifiableMap(initStreamers);");
		out.println("applyConfig(new StreamerConfig());");
		out.outdent();
		out.println("}");

		out.commit(logger);
		return packageName + "." + streamerImplClassName;
	}

	private void discoverParameterizedEnums(final Set<JType> discoveredTypes, final JParameterizedType type) {
		for (final JClassType t : type.getTypeArgs()) {
			final JParameterizedType pt = t.isParameterized();

			if (t.isEnum() != null) {
				discoveredTypes.add(t);
			}

			if (pt != null) {
				discoverParameterizedEnums(discoveredTypes, pt);
			}
		}
	}

	private String signature(final JType t) {
		if (t.isArray() != null) {
			return t.getJNISignature().replace('/', '.');
		} else {
			return t.getQualifiedBinaryName();
		}
	}

	private void writeGetField(final SourceWriter out, final JField f) throws UnableToCompleteException {
		final JPrimitiveType ptype = f.getType().isPrimitive();

		if (ptype != null) {
			out.println("values.@java.util.List::add(Ljava/lang/Object;)(");
			out.indent();
			switch (ptype) {
			case INT:
				out.print("@java.lang.Integer::valueOf(I)(");
				break;
			case BOOLEAN:
				out.print("@java.lang.Boolean::valueOf(Z)(");
				break;
			case DOUBLE:
				out.print("@java.lang.Double::valueOf(D)(");
				break;
			case BYTE:
				out.print("@java.lang.Byte::valueOf(B)(");
				break;
			case SHORT:
				out.print("@java.lang.Short::valueOf(S)(");
				break;
			case LONG:
				out.print("@java.lang.Long::valueOf(J)(");
				break;
			case FLOAT:
				out.print("@java.lang.Float::valueOf(F)(");
				break;
			case CHAR:
				out.print("@java.lang.Character::valueOf(C)(");
				break;
			default:
				throw new UnableToCompleteException();
			}

			out.println("obj.@" + f.getEnclosingType().getQualifiedSourceName() + "::" + f.getName() + "));");
			out.outdent();
		} else {
			out.println("values.@java.util.List::add(Ljava/lang/Object;)("
					+ "obj.@" + f.getEnclosingType().getQualifiedSourceName() + "::" + f.getName() + ");");
		}
	}

	private void writeSetField(final SourceWriter out, final JField f, final int n) throws UnableToCompleteException {
		final JPrimitiveType ptype = f.getType().isPrimitive();
		out.print("obj.@" + f.getEnclosingType().getQualifiedSourceName() + "::" + f.getName() + " = values.@java.util.List::get(I)(" + n + ")");

		if (ptype != null) {
			switch (ptype) {
			case INT:
				out.println(".@java.lang.Integer::intValue()();");
				break;
			case BOOLEAN:
				out.println(".@java.lang.Boolean::booleanValue()();");
				break;
			case DOUBLE:
				out.println(".@java.lang.Double::doubleValue()();");
				break;
			case BYTE:
				out.println(".@java.lang.Byte::byteValue()();");
				break;
			case SHORT:
				out.println(".@java.lang.Short::shortValue()();");
				break;
			case LONG:
				out.println(".@java.lang.Long::longValue()();");
				break;
			case FLOAT:
				out.println(".@java.lang.Float::floatValue()();");
				break;
			case CHAR:
				out.println(".@java.lang.Character::charValue()();");
				break;
			default:
				throw new UnableToCompleteException();
			}
		} else {
			out.println(";");
		}
	}
}
