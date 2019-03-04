/*
 * Copyright (c) 1994, 2010, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package java.lang;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Member;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.InvocationTargetException;
import java.lang.ref.SoftReference;
import java.io.InputStream;
import java.io.ObjectStreamField;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.LinkedList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import sun.misc.Unsafe;
import sun.reflect.CallerSensitive;
import sun.reflect.ConstantPool;
import sun.reflect.Reflection;
import sun.reflect.ReflectionFactory;
import sun.reflect.SignatureIterator;
import sun.reflect.generics.factory.CoreReflectionFactory;
import sun.reflect.generics.factory.GenericsFactory;
import sun.reflect.generics.repository.ClassRepository;
import sun.reflect.generics.repository.MethodRepository;
import sun.reflect.generics.repository.ConstructorRepository;
import sun.reflect.generics.scope.ClassScope;
import sun.security.util.SecurityConstants;
import java.lang.annotation.Annotation;
import java.lang.reflect.Proxy;
import sun.reflect.annotation.*;
import sun.reflect.misc.ReflectUtil;

/**
 * Class的实例代表了在运行中的类或者接口。
 * 枚举是一种类，注解是一种接口。每一个相同类型和维度的数组也共享同一个Class对象。基础类型和关键字void也表示为Class对象??
 * Instances of the class represent classes and interfaces in a running Java application. An enum is a kind of class and an annotation is a kind
 * of interface. Every array also belongs to a class that is reflected as a Class object that is shared by all arrays with the same element type
 * and number of dimensions. The primitive Java types boolean, byte, char, short, int, long, float, and double, and the keyword void are also
 * represented as Class objects.
 *
 * 该类没有public的构造函数，只由JVM在class文件加载时构建Class对象
 * Class has no public constructor. Instead Class objects are constructed automatically by the Java Virtual Machine as classes are loaded and by
 * calls to the defineClass method in the class loader.
 *
 * The following example uses a Class object to print the class name of an object:
 *
 *     void printClassName(Object obj) {
 *         System.out.println("The class of " + obj + " is " + obj.getClass().getName());
 *     }
 *
 * It is also possible to get the Class object for a named type (or for void) using a class literal. See Section 15.8.2 of 《The Java Language
 * Specification》. For example:
 *
 *     System.out.println("The name of class Foo is: "+Foo.class.getName());
 *
 * @param <T> the type of the class modeled by this Class object. For example, the type of String.class is Class<String>. Use Class<?> if the class
 *          being modeled is unknown.
 *
 * @author  unascribed
 * @see     java.lang.ClassLoader#defineClass(byte[], int, int)
 * @since   JDK1.0
 */
public final class Class<T> implements java.io.Serializable,
                                java.lang.reflect.GenericDeclaration,
                                java.lang.reflect.Type,
                                java.lang.reflect.AnnotatedElement {
    private static final int ANNOTATION= 0x00002000;
    private static final int ENUM      = 0x00004000;
    private static final int SYNTHETIC = 0x00001000;

    private static native void registerNatives();
    static {
        registerNatives();
    }

    /*
     * Constructor. Only the Java Virtual Machine creates Class objects.
     */
    private Class() {}


    /**
     * Converts the object to a string. The string representation is the string "class" or "interface", followed by a space, and then by
     * the fully qualified name of the class in the format returned by getName. If this Class object represents a primitive type, this
     * method returns the name of the primitive type. If this Class object represents void this method returns "void".
     *
     * @return a string representation of this class object.
     */
    public String toString() {
        return (isInterface() ? "interface " : (isPrimitive() ? "" : "class ")) + getName();
    }


    /**
     * Returns the Class object associated with the class or interface with the given string name. Invoking this method is equivalent to:
     * Class.forName(className, true, currentLoader)
     *
     * where currentLoader denotes the defining class loader of the current class.
     *
     * For example, the following code fragment returns the runtime Class descriptor for the class named java.lang.Thread:
     * Class t = Class.forName("java.lang.Thread")
     *
     * 调用forName()会引发该类被初始化，因为initialize默认是true.
     * A call to forName("X") causes the class named X to be initialized.
     *
     * @param      className   the fully qualified name of the desired class.
     * @return     the Class object for the class with the specified name.
     * @exception LinkageError if the linkage fails
     * @exception ExceptionInInitializerError if the initialization provoked by this method fails
     * @exception ClassNotFoundException if the class cannot be located
     */
    @CallerSensitive
    public static Class<?> forName(String className) throws ClassNotFoundException {
        return forName0(className, true, ClassLoader.getClassLoader(Reflection.getCallerClass()));
    }


    /**
     * Returns the Class object associated with the class or interface with the given string name, using the given class loader. Given the
     * fully qualified name for a class or interface (in the same format returned by getName) this method attempts to locate, load, and link
     * the class or interface. The specified class loader is used to load the class or interface. If the parameter loader is null, the class
     * is loaded through the bootstrap class loader. The class is initialized only if the initialize parameter is true and if it has not been
     * initialized earlier.
     *
     * If name denotes a primitive type or void, an attempt will be made to locate a user-defined class in the unnamed package whose name is
     * name. Therefore, this method cannot be used to obtain any of the Class objects representing primitive types or void.
     *
     * If name denotes an array class, the component type of the array class is loaded but not initialized.
     *
     * For example, in an instance method the expression: Class.forName("Foo") is equivalent to:
     * Class.forName("Foo", true, this.getClass().getClassLoader())
     *
     * Note that this method throws errors related to loading, linking or initializing as specified in Sections 12.2, 12.3 and 12.4 of 《The
     * Java Language Specification》. Note that this method does not check whether the requested class is accessible to its caller.
     *
     * If the loader is null, and a security manager is present, and the caller's class loader is not null, then this method calls the security
     * manager's checkPermission} method with a RuntimePermission("getClassLoader") permission to ensure it's ok to access the bootstrap class
     * loader.
     *
     * @param name       fully qualified name of the desired class
     * @param initialize whether the class must be initialized
     * @param loader     class loader from which the class must be loaded
     * @return           class object representing the desired class
     *
     * @exception LinkageError if the linkage fails
     * @exception ExceptionInInitializerError if the initialization provoked by this method fails
     * @exception ClassNotFoundException if the class cannot be located by the specified class loader
     *
     * @see       java.lang.Class#forName(String)
     * @see       java.lang.ClassLoader
     * @since     1.2
     */
    @CallerSensitive
    public static Class<?> forName(String name, boolean initialize,
                                   ClassLoader loader)
        throws ClassNotFoundException
    {
        if (loader == null) {
            SecurityManager sm = System.getSecurityManager();
            if (sm != null) {
                ClassLoader ccl = ClassLoader.getClassLoader(Reflection.getCallerClass());
                if (ccl != null) {
                    sm.checkPermission(
                        SecurityConstants.GET_CLASSLOADER_PERMISSION);
                }
            }
        }
        return forName0(name, initialize, loader);
    }

    /** Called after security checks have been made. */
    private static native Class<?> forName0(String name, boolean initialize, ClassLoader loader)
        throws ClassNotFoundException;

    /**
     * Creates a new instance of the class represented by this Class object. The class is instantiated as if by a new expression with an
     * empty argument list. The class is initialized if it has not already been initialized.
     *
     * Note that this method propagates any exception thrown by the nullary constructor, including a checked exception. Use of this method
     * effectively bypasses the compile-time exception checking that would otherwise be performed by the compiler.
     *
     * The java.lang.reflect.Constructor#newInstance(java.lang.Object...) Constructor.newInstance method avoids this problem by wrapping any
     * exception thrown by the constructor in a (checked) java.lang.reflect.InvocationTargetException.
     *
     * @return     a newly allocated instance of the class represented by this object.
     * @exception  IllegalAccessException  if the class or its nullary constructor is not accessible.
     * @exception  InstantiationException if this Class represents an abstract class, an interface, an array class, a primitive type, or void;
     *                                    or if the class has no nullary constructor; or if the instantiation fails for some other reason.
     * @exception  ExceptionInInitializerError if the initialization provoked by this method fails.
     * @exception  SecurityException If a security manager, s, is present and any of the following conditions is met:
     *
     *             invocation of SecurityManager#checkMemberAccess s.checkMemberAccess(this, Member.PUBLIC) denies creation of new instances of
     *             this class
     *
     *             the caller's class loader is not the same as or an ancestor of the class loader for the current class and invocation of
     *             s.checkPackageAccess() denies access to the package of this class
     *
     */
    @CallerSensitive
    public T newInstance()
        throws InstantiationException, IllegalAccessException
    {
        if (System.getSecurityManager() != null) {
            checkMemberAccess(Member.PUBLIC, Reflection.getCallerClass(), false);
        }

        // NOTE: the following code may not be strictly correct under the current Java memory model.

        // Constructor lookup
        if (cachedConstructor == null) {
            if (this == Class.class) {
                throw new IllegalAccessException("Can not call newInstance() on the Class for java.lang.Class");
            }
            try {
                Class<?>[] empty = {};
                final Constructor<T> c = getConstructor0(empty, Member.DECLARED);
                // Disable accessibility checks on the constructor since we have to do the security check here anyway (the stack depth is wrong
                // for the Constructor's security check to work)
                java.security.AccessController.doPrivileged(
                    new java.security.PrivilegedAction<Void>() {
                        public Void run() {
                                c.setAccessible(true);
                                return null;
                            }
                        });
                cachedConstructor = c;
            } catch (NoSuchMethodException e) {
                throw new InstantiationException(getName());
            }
        }
        Constructor<T> tmpConstructor = cachedConstructor;
        // Security check (same as in java.lang.reflect.Constructor)
        int modifiers = tmpConstructor.getModifiers();
        if (!Reflection.quickCheckMemberAccess(this, modifiers)) {
            Class<?> caller = Reflection.getCallerClass();
            if (newInstanceCallerCache != caller) {
                Reflection.ensureMemberAccess(caller, this, null, modifiers);
                newInstanceCallerCache = caller;
            }
        }
        // Run constructor
        try {
            return tmpConstructor.newInstance((Object[])null);
        } catch (InvocationTargetException e) {
            Unsafe.getUnsafe().throwException(e.getTargetException());
            // Not reached
            return null;
        }
    }
    private volatile transient Constructor<T> cachedConstructor;
    private volatile transient Class<?>       newInstanceCallerCache;


    /**
     * Determines if the specified Object is assignment-compatible with the object represented by this Class. This method is the dynamic
     * equivalent of the Java language instanceof operator. The method returns true if the specified Object argument is non-null and can
     * be cast to the reference type represented by this Class object without raising a ClassCastException. It returns false otherwise.
     *
     * Specifically, if this Class object represents a declared class, this method returns true if the specified Object argument is an instance
     * of the represented class (or of any of its subclasses); it returns false otherwise. If this Class} object represents an array class, this
     * method returns true if the specified Object argument can be converted to an object of the array class by an identity conversion or by a
     * widening reference conversion; it returns false otherwise. If this Class object represents an interface, this method returns true if the
     * class or any superclass of the specified Object argument implements this interface; it returns false otherwise.
     *
     * If this Class object represents a primitive type, this method returns false.
     *
     * @param   obj the object to check
     * @return  true if obj is an instance of this class
     *
     * @since JDK1.1
     */
    public native boolean isInstance(Object obj);


    /**
     * Determines if the class or interface represented by this Class object is either the same as, or is a superclass or superinterface of,
     * the class or interface represented by the specified Class parameter. It returns true if so; otherwise it returns false. If this Class
     * object represents a primitive type, this method returns true if the specified Class parameter is exactly this Class object; otherwise
     * it returns false.
     *
     * Specifically, this method tests whether the type represented by the specified Class parameter can be converted to the type represented
     * by this Class object via an identity conversion or via a widening reference conversion. See The Java Language Specification, sections
     * 5.1.1 and 5.1.4 , for details.
     *
     * @param cls the Class object to be checked
     * @return the boolean value indicating whether objects of the type cls can be assigned to objects of this class
     * @exception NullPointerException if the specified Class parameter is null.
     * @since JDK1.1
     */
    public native boolean isAssignableFrom(Class<?> cls);


    /**
     * Determines if the specified Class object represents an interface type.
     *
     * @return  true if this object represents an interface; false otherwise.
     */
    public native boolean isInterface();


    /**
     * Determines if this Class object represents an array class.
     *
     * @return  true if this object represents an array class; false otherwise.
     * @since   JDK1.1
     */
    public native boolean isArray();


    /**
     * Determines if the specified Class object represents a primitive type.
     *
     * There are nine predefined Class objects to represent the eight primitive types and void. These are created by the Java Virtual Machine,
     * and have the same names as the primitive types that they represent, namely boolean, byte, char, short, int, long, float, and double.
     *
     * These objects may only be accessed via the following public static final variables, and are the only Class objects for which this method
     * returns true.
     *
     * @return true if and only if this class represents a primitive type
     *
     * @see     java.lang.Boolean#TYPE
     * @see     java.lang.Character#TYPE
     * @see     java.lang.Byte#TYPE
     * @see     java.lang.Short#TYPE
     * @see     java.lang.Integer#TYPE
     * @see     java.lang.Long#TYPE
     * @see     java.lang.Float#TYPE
     * @see     java.lang.Double#TYPE
     * @see     java.lang.Void#TYPE
     * @since JDK1.1
     */
    public native boolean isPrimitive();

    /**
     * Returns true if this Class object represents an annotation type. Note that if this method returns true, isInterface() would also return
     * true, as all annotation types are also interfaces.
     *
     * @return true if this class object represents an annotation type; false otherwise
     * @since 1.5
     */
    public boolean isAnnotation() {
        return (getModifiers() & ANNOTATION) != 0;
    }

    /**
     * Returns true if this class is a synthetic class; returns false otherwise.
     * @return true if and only if this class is a synthetic class as defined by the Java Language Specification.
     * @since 1.5
     */
    public boolean isSynthetic() {
        return (getModifiers() & SYNTHETIC) != 0;
    }

    /**
     * Returns the name of the entity (class, interface, array class, primitive type, or void) represented by this Class object, as a String.
     *
     * If this class object represents a reference type that is not an array type then the binary name of the class is returned, as specified
     * by 《The Java Language Specification》.
     *
     * If this class object represents a primitive type or void, then the name returned is a String equal to the Java language keyword
     * corresponding to the primitive type or void.
     *
     * If this class object represents a class of arrays, then the internal form of the name consists of the name of the element type preceded
     * by one or more '[' characters representing the depth of the array nesting. The encoding of element type names is as follows:
     *
     * Element Type         Encoding
     * boolean              Z
     * byte                 B
     * char                 C
     * double               D
     * float                F
     * int                  I
     * long                 J
     * short                S
     * class or interface   Lclassname
     *
     * The class or interface name classname is the binary name of the class specified above. Examples:
     * String.class.getName()                               returns "java.lang.String"
     * byte.class.getName()                                 returns "byte"
     * (new Object[3]).getClass().getName()                 returns "[Ljava.lang.Object;"
     * (new int[3][4][5][6][7][8][9]).getClass().getName()  returns "[[[[[[[I"
     *
     * @return  the name of the class or interface represented by this object.
     */
    public String getName() {
        String name = this.name;
        if (name == null)
            this.name = name = getName0();
        return name;
    }

    // cache the name to reduce the number of calls into the VM
    private transient String name;
    private native String getName0();

    /**
     * Returns the class loader for the class. Some implementations may use null to represent the bootstrap class loader. This method will return
     * null in such implementations if this class was loaded by the bootstrap class loader.
     *
     * If a security manager is present, and the caller's class loader is not null and the caller's class loader is not the same as or an ancestor
     * of the class loader for the class whose class loader is requested, then this method calls the security manager's checkPermission method with
     * a RuntimePermission("getClassLoader") permission to ensure it's ok to access the class loader for the class.
     *
     * If this object represents a primitive type or void, null is returned.
     *
     * @return  the class loader that loaded the class or interface represented by this object.
     * @throws SecurityException if a security manager exists and its checkPermission method denies access to the class loader for the class.
     * @see java.lang.ClassLoader
     * @see SecurityManager#checkPermission
     * @see java.lang.RuntimePermission
     */
    @CallerSensitive
    public ClassLoader getClassLoader() {
        ClassLoader cl = getClassLoader0();
        if (cl == null)
            return null;
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            ClassLoader.checkClassLoaderPermission(cl, Reflection.getCallerClass());
        }
        return cl;
    }

    // Package-private to allow ClassLoader access
    native ClassLoader getClassLoader0();


    /**
     * Returns an array of TypeVariable objects that represent the type variables declared by the generic declaration represented by this
     * GenericDeclaration object, in declaration order. Returns an array of length 0 if the underlying generic declaration declares no type
     * variables.
     *
     * @return an array of TypeVariable objects that represent the type variables declared by this generic declaration
     * @throws java.lang.reflect.GenericSignatureFormatError if the generic signature of this generic declaration does not conform to
     *                                                       the format specified in 《The Java Virtual Machine Specification》
     * @since 1.5
     */
    public TypeVariable<Class<T>>[] getTypeParameters() {
        if (getGenericSignature() != null)
            return (TypeVariable<Class<T>>[])getGenericInfo().getTypeParameters();
        else
            return (TypeVariable<Class<T>>[])new TypeVariable<?>[0];
    }


    /**
     * Returns the Class} representing the superclass of the entity
     * (class, interface, primitive type or void) represented by this
     * Class}.  If this Class} represents either the
     * Object} class, an interface, a primitive type, or void, then
     * null is returned.  If this object represents an array class then the
     * Class} object representing the Object} class is
     * returned.
     *
     * @return the superclass of the class represented by this object.
     */
    public native Class<? super T> getSuperclass();


    /**
     * Returns the Type representing the direct superclass of the entity class, interface, primitive type or void represented by this Class.
     *
     * If the superclass is a parameterized type, the Type object returned must accurately reflect the actual type parameters used in the
     * source code. The parameterized type representing the superclass is created if it had not been created before. See the declaration of
     * java.lang.reflect.ParameterizedType ParameterizedType for the semantics of the creation process for parameterized types. If this Class
     * represents either the Object class, an interface, a primitive type, or void, then null is returned. If this object represents an array
     * class then the Class object representing the Object class is returned.
     *
     * @throws java.lang.reflect.GenericSignatureFormatError if the generic class signature does not conform to the format specified in
     *                                                      《The Java Virtual Machine Specification》
     * @throws TypeNotPresentException if the generic superclass refers to a non-existent type declaration
     * @throws java.lang.reflect.MalformedParameterizedTypeException if the generic superclass refers to a parameterized type that cannot be
     *                                                               instantiated for any reason
     * @return the superclass of the class represented by this object
     * @since 1.5
     */
    public Type getGenericSuperclass() {
        if (getGenericSignature() != null) {
            // Historical irregularity:
            // Generic signature marks interfaces with superclass = Object but this API returns null for interfaces
            if (isInterface())
                return null;
            return getGenericInfo().getSuperclass();
        } else
            return getSuperclass();
    }

    /**
     * Gets the package for this class.  The class loader of this class is used
     * to find the package.  If the class was loaded by the bootstrap class
     * loader the set of packages loaded from CLASSPATH is searched to find the
     * package of the class. Null is returned if no package object was created
     * by the class loader of this class.
     *
     * Packages have attributes for versions and specifications only if the
     * information was defined in the manifests that accompany the classes, and
     * if the class loader created the package instance with the attributes
     * from the manifest.
     *
     * @return the package of the class, or null if no package
     *         information is available from the archive or codebase.
     */
    public Package getPackage() {
        return Package.getPackage(this);
    }


    /**
     * Determines the interfaces implemented by the class or interface
     * represented by this object.
     *
     * If this object represents a class, the return value is an array
     * containing objects representing all interfaces implemented by the
     * class. The order of the interface objects in the array corresponds to
     * the order of the interface names in the implements} clause
     * of the declaration of the class represented by this object. For
     * example, given the declaration:
     * <blockquote>
     * class Shimmer implements FloorWax, DessertTopping { ... }}
     * </blockquote>
     * suppose the value of s} is an instance of
     * Shimmer}; the value of the expression:
     * <blockquote>
     * s.getClass().getInterfaces()[0]}
     * </blockquote>
     * is the Class} object that represents interface
     * FloorWax}; and the value of:
     * <blockquote>
     * s.getClass().getInterfaces()[1]}
     * </blockquote>
     * is the Class} object that represents interface
     * DessertTopping}.
     *
     * If this object represents an interface, the array contains objects
     * representing all interfaces extended by the interface. The order of the
     * interface objects in the array corresponds to the order of the interface
     * names in the extends} clause of the declaration of the
     * interface represented by this object.
     *
     * If this object represents a class or interface that implements no
     * interfaces, the method returns an array of length 0.
     *
     * If this object represents a primitive type or void, the method
     * returns an array of length 0.
     *
     * @return an array of interfaces implemented by this class.
     */
    public native Class<?>[] getInterfaces();

    /**
     * Returns the Type}s representing the interfaces
     * directly implemented by the class or interface represented by
     * this object.
     *
     * If a superinterface is a parameterized type, the
     * Type} object returned for it must accurately reflect
     * the actual type parameters used in the source code. The
     * parameterized type representing each superinterface is created
     * if it had not been created before. See the declaration of
     * {@link java.lang.reflect.ParameterizedType ParameterizedType}
     * for the semantics of the creation process for parameterized
     * types.
     *
     * If this object represents a class, the return value is an
     * array containing objects representing all interfaces
     * implemented by the class. The order of the interface objects in
     * the array corresponds to the order of the interface names in
     * the implements} clause of the declaration of the class
     * represented by this object.  In the case of an array class, the
     * interfaces Cloneable} and Serializable} are
     * returned in that order.
     *
     * If this object represents an interface, the array contains
     * objects representing all interfaces directly extended by the
     * interface.  The order of the interface objects in the array
     * corresponds to the order of the interface names in the
     * extends} clause of the declaration of the interface
     * represented by this object.
     *
     * If this object represents a class or interface that
     * implements no interfaces, the method returns an array of length
     * 0.
     *
     * If this object represents a primitive type or void, the
     * method returns an array of length 0.
     *
     * @throws java.lang.reflect.GenericSignatureFormatError
     *     if the generic class signature does not conform to the format
     *     specified in
     *     <cite>The Java&trade; Virtual Machine Specification</cite>
     * @throws TypeNotPresentException if any of the generic
     *     superinterfaces refers to a non-existent type declaration
     * @throws java.lang.reflect.MalformedParameterizedTypeException
     *     if any of the generic superinterfaces refer to a parameterized
     *     type that cannot be instantiated for any reason
     * @return an array of interfaces implemented by this class
     * @since 1.5
     */
    public Type[] getGenericInterfaces() {
        if (getGenericSignature() != null)
            return getGenericInfo().getSuperInterfaces();
        else
            return getInterfaces();
    }


    /**
     * Returns the Class} representing the component type of an
     * array.  If this class does not represent an array class this method
     * returns null.
     *
     * @return the Class} representing the component type of this
     * class if this class is an array
     * @see     java.lang.reflect.Array
     * @since JDK1.1
     */
    public native Class<?> getComponentType();


    /**
     * Returns the Java language modifiers for this class or interface, encoded
     * in an integer. The modifiers consist of the Java Virtual Machine's
     * constants for public}, protected},
     * private}, final}, static},
     * abstract} and interface}; they should be decoded
     * using the methods of class Modifier}.
     *
     * If the underlying class is an array class, then its
     * public}, private} and protected}
     * modifiers are the same as those of its component type.  If this
     * Class} represents a primitive type or void, its
     * public} modifier is always true}, and its
     * protected} and private} modifiers are always
     * false}. If this object represents an array class, a
     * primitive type or void, then its final} modifier is always
     * true} and its interface modifier is always
     * false}. The values of its other modifiers are not determined
     * by this specification.
     *
     * The modifier encodings are defined in <em>The Java Virtual Machine
     * Specification</em>, table 4.1.
     *
     * @return the int} representing the modifiers for this class
     * @see     java.lang.reflect.Modifier
     * @since JDK1.1
     */
    public native int getModifiers();


    /**
     * Gets the signers of this class.
     *
     * @return  the signers of this class, or null if there are no signers.  In
     *          particular, this method returns null if this object represents
     *          a primitive type or void.
     * @since   JDK1.1
     */
    public native Object[] getSigners();


    /**
     * Set the signers of this class.
     */
    native void setSigners(Object[] signers);


    /**
     * If this Class} object represents a local or anonymous
     * class within a method, returns a {@link
     * java.lang.reflect.Method Method} object representing the
     * immediately enclosing method of the underlying class. Returns
     * null} otherwise.
     *
     * In particular, this method returns null} if the underlying
     * class is a local or anonymous class immediately enclosed by a type
     * declaration, instance initializer or static initializer.
     *
     * @return the immediately enclosing method of the underlying class, if
     *     that class is a local or anonymous class; otherwise null}.
     * @since 1.5
     */
    @CallerSensitive
    public Method getEnclosingMethod() {
        EnclosingMethodInfo enclosingInfo = getEnclosingMethodInfo();

        if (enclosingInfo == null)
            return null;
        else {
            if (!enclosingInfo.isMethod())
                return null;

            MethodRepository typeInfo = MethodRepository.make(enclosingInfo.getDescriptor(),
                                                              getFactory());
            Class<?>   returnType       = toClass(typeInfo.getReturnType());
            Type []    parameterTypes   = typeInfo.getParameterTypes();
            Class<?>[] parameterClasses = new Class<?>[parameterTypes.length];

            // Convert Types to Classes; returned types *should*
            // be class objects since the methodDescriptor's used
            // don't have generics information
            for(int i = 0; i < parameterClasses.length; i++)
                parameterClasses[i] = toClass(parameterTypes[i]);

            // Perform access check
            Class<?> enclosingCandidate = enclosingInfo.getEnclosingClass();
            // be very careful not to change the stack depth of this
            // checkMemberAccess call for security reasons
            // see java.lang.SecurityManager.checkMemberAccess
            //
            // Note that we need to do this on the enclosing class
            enclosingCandidate.checkMemberAccess(Member.DECLARED,
                                                 Reflection.getCallerClass(), true);
            /*
             * Loop over all declared methods; match method name,
             * number of and type of parameters, *and* return
             * type.  Matching return type is also necessary
             * because of covariant returns, etc.
             */
            for(Method m: enclosingCandidate.getDeclaredMethods()) {
                if (m.getName().equals(enclosingInfo.getName()) ) {
                    Class<?>[] candidateParamClasses = m.getParameterTypes();
                    if (candidateParamClasses.length == parameterClasses.length) {
                        boolean matches = true;
                        for(int i = 0; i < candidateParamClasses.length; i++) {
                            if (!candidateParamClasses[i].equals(parameterClasses[i])) {
                                matches = false;
                                break;
                            }
                        }

                        if (matches) { // finally, check return type
                            if (m.getReturnType().equals(returnType) )
                                return m;
                        }
                    }
                }
            }

            throw new InternalError("Enclosing method not found");
        }
    }

    private native Object[] getEnclosingMethod0();

    private EnclosingMethodInfo getEnclosingMethodInfo() {
        Object[] enclosingInfo = getEnclosingMethod0();
        if (enclosingInfo == null)
            return null;
        else {
            return new EnclosingMethodInfo(enclosingInfo);
        }
    }

    private final static class EnclosingMethodInfo {
        private Class<?> enclosingClass;
        private String name;
        private String descriptor;

        private EnclosingMethodInfo(Object[] enclosingInfo) {
            if (enclosingInfo.length != 3)
                throw new InternalError("Malformed enclosing method information");
            try {
                // The array is expected to have three elements:

                // the immediately enclosing class
                enclosingClass = (Class<?>) enclosingInfo[0];
                assert(enclosingClass != null);

                // the immediately enclosing method or constructor's
                // name (can be null).
                name            = (String)   enclosingInfo[1];

                // the immediately enclosing method or constructor's
                // descriptor (null iff name is).
                descriptor      = (String)   enclosingInfo[2];
                assert((name != null && descriptor != null) || name == descriptor);
            } catch (ClassCastException cce) {
                throw new InternalError("Invalid type in enclosing method information");
            }
        }

        boolean isPartial() {
            return enclosingClass == null || name == null || descriptor == null;
        }

        boolean isConstructor() { return !isPartial() && "<init>".equals(name); }

        boolean isMethod() { return !isPartial() && !isConstructor() && !"<clinit>".equals(name); }

        Class<?> getEnclosingClass() { return enclosingClass; }

        String getName() { return name; }

        String getDescriptor() { return descriptor; }

    }

    private static Class<?> toClass(Type o) {
        if (o instanceof GenericArrayType)
            return Array.newInstance(toClass(((GenericArrayType)o).getGenericComponentType()),
                                     0)
                .getClass();
        return (Class<?>)o;
     }

    /**
     * If this Class} object represents a local or anonymous
     * class within a constructor, returns a {@link
     * java.lang.reflect.Constructor Constructor} object representing
     * the immediately enclosing constructor of the underlying
     * class. Returns null} otherwise.  In particular, this
     * method returns null} if the underlying class is a local
     * or anonymous class immediately enclosed by a type declaration,
     * instance initializer or static initializer.
     *
     * @return the immediately enclosing constructor of the underlying class, if
     *     that class is a local or anonymous class; otherwise null}.
     * @since 1.5
     */
    @CallerSensitive
    public Constructor<?> getEnclosingConstructor() {
        EnclosingMethodInfo enclosingInfo = getEnclosingMethodInfo();

        if (enclosingInfo == null)
            return null;
        else {
            if (!enclosingInfo.isConstructor())
                return null;

            ConstructorRepository typeInfo = ConstructorRepository.make(enclosingInfo.getDescriptor(),
                                                                        getFactory());
            Type []    parameterTypes   = typeInfo.getParameterTypes();
            Class<?>[] parameterClasses = new Class<?>[parameterTypes.length];

            // Convert Types to Classes; returned types *should*
            // be class objects since the methodDescriptor's used
            // don't have generics information
            for(int i = 0; i < parameterClasses.length; i++)
                parameterClasses[i] = toClass(parameterTypes[i]);

            // Perform access check
            Class<?> enclosingCandidate = enclosingInfo.getEnclosingClass();
            // be very careful not to change the stack depth of this
            // checkMemberAccess call for security reasons
            // see java.lang.SecurityManager.checkMemberAccess
            //
            // Note that we need to do this on the enclosing class
            enclosingCandidate.checkMemberAccess(Member.DECLARED,
                                                 Reflection.getCallerClass(), true);
            /*
             * Loop over all declared constructors; match number
             * of and type of parameters.
             */
            for(Constructor<?> c: enclosingCandidate.getDeclaredConstructors()) {
                Class<?>[] candidateParamClasses = c.getParameterTypes();
                if (candidateParamClasses.length == parameterClasses.length) {
                    boolean matches = true;
                    for(int i = 0; i < candidateParamClasses.length; i++) {
                        if (!candidateParamClasses[i].equals(parameterClasses[i])) {
                            matches = false;
                            break;
                        }
                    }

                    if (matches)
                        return c;
                }
            }

            throw new InternalError("Enclosing constructor not found");
        }
    }


    /**
     * If the class or interface represented by this Class} object
     * is a member of another class, returns the Class} object
     * representing the class in which it was declared.  This method returns
     * null if this class or interface is not a member of any other class.  If
     * this Class} object represents an array class, a primitive
     * type, or void,then this method returns null.
     *
     * @return the declaring class for this class
     * @since JDK1.1
     */
    @CallerSensitive
    public Class<?> getDeclaringClass() {
        final Class<?> candidate = getDeclaringClass0();

        if (candidate != null)
            candidate.checkPackageAccess(
                    ClassLoader.getClassLoader(Reflection.getCallerClass()), true);
        return candidate;
    }

    private native Class<?> getDeclaringClass0();


    /**
     * Returns the immediately enclosing class of the underlying
     * class.  If the underlying class is a top level class this
     * method returns null}.
     * @return the immediately enclosing class of the underlying class
     * @since 1.5
     */
    @CallerSensitive
    public Class<?> getEnclosingClass() {
        // There are five kinds of classes (or interfaces):
        // a) Top level classes
        // b) Nested classes (static member classes)
        // c) Inner classes (non-static member classes)
        // d) Local classes (named classes declared within a method)
        // e) Anonymous classes


        // JVM Spec 4.8.6: A class must have an EnclosingMethod
        // attribute if and only if it is a local class or an
        // anonymous class.
        EnclosingMethodInfo enclosingInfo = getEnclosingMethodInfo();
        Class<?> enclosingCandidate;

        if (enclosingInfo == null) {
            // This is a top level or a nested class or an inner class (a, b, or c)
            enclosingCandidate = getDeclaringClass();
        } else {
            Class<?> enclosingClass = enclosingInfo.getEnclosingClass();
            // This is a local class or an anonymous class (d or e)
            if (enclosingClass == this || enclosingClass == null)
                throw new InternalError("Malformed enclosing method information");
            else
                enclosingCandidate = enclosingClass;
        }

        if (enclosingCandidate != null)
            enclosingCandidate.checkPackageAccess(
                    ClassLoader.getClassLoader(Reflection.getCallerClass()), true);
        return enclosingCandidate;
    }

    /**
     * Returns the simple name of the underlying class as given in the
     * source code. Returns an empty string if the underlying class is
     * anonymous.
     *
     * The simple name of an array is the simple name of the
     * component type with "[]" appended.  In particular the simple
     * name of an array whose component type is anonymous is "[]".
     *
     * @return the simple name of the underlying class
     * @since 1.5
     */
    public String getSimpleName() {
        if (isArray())
            return getComponentType().getSimpleName()+"[]";

        String simpleName = getSimpleBinaryName();
        if (simpleName == null) { // top level class
            simpleName = getName();
            return simpleName.substring(simpleName.lastIndexOf(".")+1); // strip the package name
        }
        // According to JLS3 "Binary Compatibility" (13.1) the binary
        // name of non-package classes (not top level) is the binary
        // name of the immediately enclosing class followed by a '$' followed by:
        // (for nested and inner classes): the simple name.
        // (for local classes): 1 or more digits followed by the simple name.
        // (for anonymous classes): 1 or more digits.

        // Since getSimpleBinaryName() will strip the binary name of
        // the immediatly enclosing class, we are now looking at a
        // string that matches the regular expression "\$[0-9]*"
        // followed by a simple name (considering the simple of an
        // anonymous class to be the empty string).

        // Remove leading "\$[0-9]*" from the name
        int length = simpleName.length();
        if (length < 1 || simpleName.charAt(0) != '$')
            throw new InternalError("Malformed class name");
        int index = 1;
        while (index < length && isAsciiDigit(simpleName.charAt(index)))
            index++;
        // Eventually, this is the empty string iff this is an anonymous class
        return simpleName.substring(index);
    }

    /**
     * Character.isDigit answers true} to some non-ascii
     * digits.  This one does not.
     */
    private static boolean isAsciiDigit(char c) {
        return '0' <= c && c <= '9';
    }

    /**
     * Returns the canonical name of the underlying class as
     * defined by the Java Language Specification.  Returns null if
     * the underlying class does not have a canonical name (i.e., if
     * it is a local or anonymous class or an array whose component
     * type does not have a canonical name).
     * @return the canonical name of the underlying class if it exists, and
     * null} otherwise.
     * @since 1.5
     */
    public String getCanonicalName() {
        if (isArray()) {
            String canonicalName = getComponentType().getCanonicalName();
            if (canonicalName != null)
                return canonicalName + "[]";
            else
                return null;
        }
        if (isLocalOrAnonymousClass())
            return null;
        Class<?> enclosingClass = getEnclosingClass();
        if (enclosingClass == null) { // top level class
            return getName();
        } else {
            String enclosingName = enclosingClass.getCanonicalName();
            if (enclosingName == null)
                return null;
            return enclosingName + "." + getSimpleName();
        }
    }

    /**
     * Returns true} if and only if the underlying class
     * is an anonymous class.
     *
     * @return true} if and only if this class is an anonymous class.
     * @since 1.5
     */
    public boolean isAnonymousClass() {
        return "".equals(getSimpleName());
    }

    /**
     * Returns true} if and only if the underlying class
     * is a local class.
     *
     * @return true} if and only if this class is a local class.
     * @since 1.5
     */
    public boolean isLocalClass() {
        return isLocalOrAnonymousClass() && !isAnonymousClass();
    }

    /**
     * Returns true} if and only if the underlying class
     * is a member class.
     *
     * @return true} if and only if this class is a member class.
     * @since 1.5
     */
    public boolean isMemberClass() {
        return getSimpleBinaryName() != null && !isLocalOrAnonymousClass();
    }

    /**
     * Returns the "simple binary name" of the underlying class, i.e.,
     * the binary name without the leading enclosing class name.
     * Returns null} if the underlying class is a top level
     * class.
     */
    private String getSimpleBinaryName() {
        Class<?> enclosingClass = getEnclosingClass();
        if (enclosingClass == null) // top level class
            return null;
        // Otherwise, strip the enclosing class' name
        try {
            return getName().substring(enclosingClass.getName().length());
        } catch (IndexOutOfBoundsException ex) {
            throw new InternalError("Malformed class name");
        }
    }

    /**
     * Returns true} if this is a local class or an anonymous
     * class.  Returns false} otherwise.
     */
    private boolean isLocalOrAnonymousClass() {
        // JVM Spec 4.8.6: A class must have an EnclosingMethod
        // attribute if and only if it is a local class or an
        // anonymous class.
        return getEnclosingMethodInfo() != null;
    }

    /**
     * Returns an array containing Class} objects representing all
     * the public classes and interfaces that are members of the class
     * represented by this Class} object.  This includes public
     * class and interface members inherited from superclasses and public class
     * and interface members declared by the class.  This method returns an
     * array of length 0 if this Class} object has no public member
     * classes or interfaces.  This method also returns an array of length 0 if
     * this Class} object represents a primitive type, an array
     * class, or void.
     *
     * @return the array of Class} objects representing the public
     * members of this class
     * @exception  SecurityException
     *             If a security manager, <i>s</i>, is present and any of the
     *             following conditions is met:
     *
     *             <ul>
     *
     *             <li> invocation of
     *             {@link SecurityManager#checkMemberAccess
     *             s.checkMemberAccess(this, Member.PUBLIC)} method
     *             denies access to the classes within this class
     *
     *             <li> the caller's class loader is not the same as or an
     *             ancestor of the class loader for the current class and
     *             invocation of {@link SecurityManager#checkPackageAccess
     *             s.checkPackageAccess()} denies access to the package
     *             of this class
     *
     *             </ul>
     *
     * @since JDK1.1
     */
    @CallerSensitive
    public Class<?>[] getClasses() {
        // be very careful not to change the stack depth of this
        // checkMemberAccess call for security reasons
        // see java.lang.SecurityManager.checkMemberAccess
        checkMemberAccess(Member.PUBLIC, Reflection.getCallerClass(), false);

        // Privileged so this implementation can look at DECLARED classes,
        // something the caller might not have privilege to do.  The code here
        // is allowed to look at DECLARED classes because (1) it does not hand
        // out anything other than public members and (2) public member access
        // has already been ok'd by the SecurityManager.

        return java.security.AccessController.doPrivileged(
            new java.security.PrivilegedAction<Class<?>[]>() {
                public Class[] run() {
                    List<Class<?>> list = new ArrayList<>();
                    Class<?> currentClass = Class.this;
                    while (currentClass != null) {
                        Class<?>[] members = currentClass.getDeclaredClasses();
                        for (int i = 0; i < members.length; i++) {
                            if (Modifier.isPublic(members[i].getModifiers())) {
                                list.add(members[i]);
                            }
                        }
                        currentClass = currentClass.getSuperclass();
                    }
                    return list.toArray(new Class[0]);
                }
            });
    }


    /**
     * Returns an array containing Field} objects reflecting all
     * the accessible public fields of the class or interface represented by
     * this Class} object.  The elements in the array returned are
     * not sorted and are not in any particular order.  This method returns an
     * array of length 0 if the class or interface has no accessible public
     * fields, or if it represents an array class, a primitive type, or void.
     *
     * Specifically, if this Class} object represents a class,
     * this method returns the public fields of this class and of all its
     * superclasses.  If this Class} object represents an
     * interface, this method returns the fields of this interface and of all
     * its superinterfaces.
     *
     * The implicit length field for array class is not reflected by this
     * method. User code should use the methods of class Array} to
     * manipulate arrays.
     *
     * See <em>The Java Language Specification</em>, sections 8.2 and 8.3.
     *
     * @return the array of Field} objects representing the
     * public fields
     * @exception  SecurityException
     *             If a security manager, <i>s</i>, is present and any of the
     *             following conditions is met:
     *
     *             <ul>
     *
     *             <li> invocation of
     *             {@link SecurityManager#checkMemberAccess
     *             s.checkMemberAccess(this, Member.PUBLIC)} denies
     *             access to the fields within this class
     *
     *             <li> the caller's class loader is not the same as or an
     *             ancestor of the class loader for the current class and
     *             invocation of {@link SecurityManager#checkPackageAccess
     *             s.checkPackageAccess()} denies access to the package
     *             of this class
     *
     *             </ul>
     *
     * @since JDK1.1
     */
    @CallerSensitive
    public Field[] getFields() throws SecurityException {
        // be very careful not to change the stack depth of this
        // checkMemberAccess call for security reasons
        // see java.lang.SecurityManager.checkMemberAccess
        checkMemberAccess(Member.PUBLIC, Reflection.getCallerClass(), true);
        return copyFields(privateGetPublicFields(null));
    }


    /**
     * Returns an array containing Method} objects reflecting all
     * the public <em>member</em> methods of the class or interface represented
     * by this Class} object, including those declared by the class
     * or interface and those inherited from superclasses and
     * superinterfaces.  Array classes return all the (public) member methods
     * inherited from the Object} class.  The elements in the array
     * returned are not sorted and are not in any particular order.  This
     * method returns an array of length 0 if this Class} object
     * represents a class or interface that has no public member methods, or if
     * this Class} object represents a primitive type or void.
     *
     * The class initialization method <clinit>} is not
     * included in the returned array. If the class declares multiple public
     * member methods with the same parameter types, they are all included in
     * the returned array.
     *
     * See <em>The Java Language Specification</em>, sections 8.2 and 8.4.
     *
     * @return the array of Method} objects representing the
     * public methods of this class
     * @exception  SecurityException
     *             If a security manager, <i>s</i>, is present and any of the
     *             following conditions is met:
     *
     *             <ul>
     *
     *             <li> invocation of
     *             {@link SecurityManager#checkMemberAccess
     *             s.checkMemberAccess(this, Member.PUBLIC)} denies
     *             access to the methods within this class
     *
     *             <li> the caller's class loader is not the same as or an
     *             ancestor of the class loader for the current class and
     *             invocation of {@link SecurityManager#checkPackageAccess
     *             s.checkPackageAccess()} denies access to the package
     *             of this class
     *
     *             </ul>
     *
     * @since JDK1.1
     */
    @CallerSensitive
    public Method[] getMethods() throws SecurityException {
        // be very careful not to change the stack depth of this
        // checkMemberAccess call for security reasons
        // see java.lang.SecurityManager.checkMemberAccess
        checkMemberAccess(Member.PUBLIC, Reflection.getCallerClass(), true);
        return copyMethods(privateGetPublicMethods());
    }


    /**
     * Returns an array containing Constructor} objects reflecting
     * all the public constructors of the class represented by this
     * Class} object.  An array of length 0 is returned if the
     * class has no public constructors, or if the class is an array class, or
     * if the class reflects a primitive type or void.
     *
     * Note that while this method returns an array of {@code
     * Constructor<T>} objects (that is an array of constructors from
     * this class), the return type of this method is {@code
     * Constructor<?>[]} and <em>not</em> Constructor<T>[]} as
     * might be expected.  This less informative return type is
     * necessary since after being returned from this method, the
     * array could be modified to hold Constructor} objects for
     * different classes, which would violate the type guarantees of
     * Constructor<T>[]}.
     *
     * @return the array of Constructor} objects representing the
     *  public constructors of this class
     * @exception  SecurityException
     *             If a security manager, <i>s</i>, is present and any of the
     *             following conditions is met:
     *
     *             <ul>
     *
     *             <li> invocation of
     *             {@link SecurityManager#checkMemberAccess
     *             s.checkMemberAccess(this, Member.PUBLIC)} denies
     *             access to the constructors within this class
     *
     *             <li> the caller's class loader is not the same as or an
     *             ancestor of the class loader for the current class and
     *             invocation of {@link SecurityManager#checkPackageAccess
     *             s.checkPackageAccess()} denies access to the package
     *             of this class
     *
     *             </ul>
     *
     * @since JDK1.1
     */
    @CallerSensitive
    public Constructor<?>[] getConstructors() throws SecurityException {
        // be very careful not to change the stack depth of this
        // checkMemberAccess call for security reasons
        // see java.lang.SecurityManager.checkMemberAccess
        checkMemberAccess(Member.PUBLIC, Reflection.getCallerClass(), true);
        return copyConstructors(privateGetDeclaredConstructors(true));
    }


    /**
     * Returns a Field} object that reflects the specified public
     * member field of the class or interface represented by this
     * Class} object. The name} parameter is a
     * String} specifying the simple name of the desired field.
     *
     * The field to be reflected is determined by the algorithm that
     * follows.  Let C be the class represented by this object:
     * 
     *  If C declares a public field with the name specified, that is the
     *      field to be reflected.
     *  If no field was found in step 1 above, this algorithm is applied
     *      recursively to each direct superinterface of C. The direct
     *      superinterfaces are searched in the order they were declared.
     *  If no field was found in steps 1 and 2 above, and C has a
     *      superclass S, then this algorithm is invoked recursively upon S.
     *      If C has no superclass, then a NoSuchFieldException}
     *      is thrown.
     * 
     *
     * See <em>The Java Language Specification</em>, sections 8.2 and 8.3.
     *
     * @param name the field name
     * @return  the Field} object of this class specified by
     * name}
     * @exception NoSuchFieldException if a field with the specified name is
     *              not found.
     * @exception NullPointerException if name} is null}
     * @exception  SecurityException
     *             If a security manager, <i>s</i>, is present and any of the
     *             following conditions is met:
     *
     *             <ul>
     *
     *             <li> invocation of
     *             {@link SecurityManager#checkMemberAccess
     *             s.checkMemberAccess(this, Member.PUBLIC)} denies
     *             access to the field
     *
     *             <li> the caller's class loader is not the same as or an
     *             ancestor of the class loader for the current class and
     *             invocation of {@link SecurityManager#checkPackageAccess
     *             s.checkPackageAccess()} denies access to the package
     *             of this class
     *
     *             </ul>
     *
     * @since JDK1.1
     */
    @CallerSensitive
    public Field getField(String name)
        throws NoSuchFieldException, SecurityException {
        // be very careful not to change the stack depth of this
        // checkMemberAccess call for security reasons
        // see java.lang.SecurityManager.checkMemberAccess
        checkMemberAccess(Member.PUBLIC, Reflection.getCallerClass(), true);
        Field field = getField0(name);
        if (field == null) {
            throw new NoSuchFieldException(name);
        }
        return field;
    }


    /**
     * Returns a Method} object that reflects the specified public
     * member method of the class or interface represented by this
     * Class} object. The name} parameter is a
     * String} specifying the simple name of the desired method. The
     * parameterTypes} parameter is an array of Class}
     * objects that identify the method's formal parameter types, in declared
     * order. If parameterTypes} is null}, it is
     * treated as if it were an empty array.
     *
     * If the name} is "<init>};"or "<clinit>}" a
     * NoSuchMethodException} is raised. Otherwise, the method to
     * be reflected is determined by the algorithm that follows.  Let C be the
     * class represented by this object:
     * 
     *  C is searched for any matching methods. If no matching
     *      method is found, the algorithm of step 1 is invoked recursively on
     *      the superclass of C.
     *  If no method was found in step 1 above, the superinterfaces of C
     *      are searched for a matching method. If any such method is found, it
     *      is reflected.
     * 
     *
     * To find a matching method in a class C:&nbsp; If C declares exactly one
     * public method with the specified name and exactly the same formal
     * parameter types, that is the method reflected. If more than one such
     * method is found in C, and one of these methods has a return type that is
     * more specific than any of the others, that method is reflected;
     * otherwise one of the methods is chosen arbitrarily.
     *
     * Note that there may be more than one matching method in a
     * class because while the Java language forbids a class to
     * declare multiple methods with the same signature but different
     * return types, the Java virtual machine does not.  This
     * increased flexibility in the virtual machine can be used to
     * implement various language features.  For example, covariant
     * returns can be implemented with {@linkplain
     * java.lang.reflect.Method#isBridge bridge methods}; the bridge
     * method and the method being overridden would have the same
     * signature but different return types.
     *
     * See <em>The Java Language Specification</em>, sections 8.2 and 8.4.
     *
     * @param name the name of the method
     * @param parameterTypes the list of parameters
     * @return the Method} object that matches the specified
     * name} and parameterTypes}
     * @exception NoSuchMethodException if a matching method is not found
     *            or if the name is "&lt;init&gt;"or "&lt;clinit&gt;".
     * @exception NullPointerException if name} is null}
     * @exception  SecurityException
     *             If a security manager, <i>s</i>, is present and any of the
     *             following conditions is met:
     *
     *             <ul>
     *
     *             <li> invocation of
     *             {@link SecurityManager#checkMemberAccess
     *             s.checkMemberAccess(this, Member.PUBLIC)} denies
     *             access to the method
     *
     *             <li> the caller's class loader is not the same as or an
     *             ancestor of the class loader for the current class and
     *             invocation of {@link SecurityManager#checkPackageAccess
     *             s.checkPackageAccess()} denies access to the package
     *             of this class
     *
     *             </ul>
     *
     * @since JDK1.1
     */
    @CallerSensitive
    public Method getMethod(String name, Class<?>... parameterTypes)
        throws NoSuchMethodException, SecurityException {
        // be very careful not to change the stack depth of this
        // checkMemberAccess call for security reasons
        // see java.lang.SecurityManager.checkMemberAccess
        checkMemberAccess(Member.PUBLIC, Reflection.getCallerClass(), true);
        Method method = getMethod0(name, parameterTypes);
        if (method == null) {
            throw new NoSuchMethodException(getName() + "." + name + argumentTypesToString(parameterTypes));
        }
        return method;
    }


    /**
     * Returns a Constructor} object that reflects the specified
     * public constructor of the class represented by this Class}
     * object. The parameterTypes} parameter is an array of
     * Class} objects that identify the constructor's formal
     * parameter types, in declared order.
     *
     * If this Class} object represents an inner class
     * declared in a non-static context, the formal parameter types
     * include the explicit enclosing instance as the first parameter.
     *
     * The constructor to reflect is the public constructor of the class
     * represented by this Class} object whose formal parameter
     * types match those specified by parameterTypes}.
     *
     * @param parameterTypes the parameter array
     * @return the Constructor} object of the public constructor that
     * matches the specified parameterTypes}
     * @exception NoSuchMethodException if a matching method is not found.
     * @exception  SecurityException
     *             If a security manager, <i>s</i>, is present and any of the
     *             following conditions is met:
     *
     *             <ul>
     *
     *             <li> invocation of
     *             {@link SecurityManager#checkMemberAccess
     *             s.checkMemberAccess(this, Member.PUBLIC)} denies
     *             access to the constructor
     *
     *             <li> the caller's class loader is not the same as or an
     *             ancestor of the class loader for the current class and
     *             invocation of {@link SecurityManager#checkPackageAccess
     *             s.checkPackageAccess()} denies access to the package
     *             of this class
     *
     *             </ul>
     *
     * @since JDK1.1
     */
    @CallerSensitive
    public Constructor<T> getConstructor(Class<?>... parameterTypes)
        throws NoSuchMethodException, SecurityException {
        // be very careful not to change the stack depth of this
        // checkMemberAccess call for security reasons
        // see java.lang.SecurityManager.checkMemberAccess
        checkMemberAccess(Member.PUBLIC, Reflection.getCallerClass(), true);
        return getConstructor0(parameterTypes, Member.PUBLIC);
    }


    /**
     * Returns an array of Class} objects reflecting all the
     * classes and interfaces declared as members of the class represented by
     * this Class} object. This includes public, protected, default
     * (package) access, and private classes and interfaces declared by the
     * class, but excludes inherited classes and interfaces.  This method
     * returns an array of length 0 if the class declares no classes or
     * interfaces as members, or if this Class} object represents a
     * primitive type, an array class, or void.
     *
     * @return the array of Class} objects representing all the
     * declared members of this class
     * @exception  SecurityException
     *             If a security manager, <i>s</i>, is present and any of the
     *             following conditions is met:
     *
     *             <ul>
     *
     *             <li> invocation of
     *             {@link SecurityManager#checkMemberAccess
     *             s.checkMemberAccess(this, Member.DECLARED)} denies
     *             access to the declared classes within this class
     *
     *             <li> the caller's class loader is not the same as or an
     *             ancestor of the class loader for the current class and
     *             invocation of {@link SecurityManager#checkPackageAccess
     *             s.checkPackageAccess()} denies access to the package
     *             of this class
     *
     *             </ul>
     *
     * @since JDK1.1
     */
    @CallerSensitive
    public Class<?>[] getDeclaredClasses() throws SecurityException {
        // be very careful not to change the stack depth of this
        // checkMemberAccess call for security reasons
        // see java.lang.SecurityManager.checkMemberAccess
        checkMemberAccess(Member.DECLARED, Reflection.getCallerClass(), false);
        return getDeclaredClasses0();
    }


    /**
     * Returns an array of Field} objects reflecting all the fields
     * declared by the class or interface represented by this
     * Class} object. This includes public, protected, default
     * (package) access, and private fields, but excludes inherited fields.
     * The elements in the array returned are not sorted and are not in any
     * particular order.  This method returns an array of length 0 if the class
     * or interface declares no fields, or if this Class} object
     * represents a primitive type, an array class, or void.
     *
     * See <em>The Java Language Specification</em>, sections 8.2 and 8.3.
     *
     * @return    the array of Field} objects representing all the
     * declared fields of this class
     * @exception  SecurityException
     *             If a security manager, <i>s</i>, is present and any of the
     *             following conditions is met:
     *
     *             <ul>
     *
     *             <li> invocation of
     *             {@link SecurityManager#checkMemberAccess
     *             s.checkMemberAccess(this, Member.DECLARED)} denies
     *             access to the declared fields within this class
     *
     *             <li> the caller's class loader is not the same as or an
     *             ancestor of the class loader for the current class and
     *             invocation of {@link SecurityManager#checkPackageAccess
     *             s.checkPackageAccess()} denies access to the package
     *             of this class
     *
     *             </ul>
     *
     * @since JDK1.1
     */
    @CallerSensitive
    public Field[] getDeclaredFields() throws SecurityException {
        // be very careful not to change the stack depth of this
        // checkMemberAccess call for security reasons
        // see java.lang.SecurityManager.checkMemberAccess
        checkMemberAccess(Member.DECLARED, Reflection.getCallerClass(), true);
        return copyFields(privateGetDeclaredFields(false));
    }


    /**
     * Returns an array of Method} objects reflecting all the
     * methods declared by the class or interface represented by this
     * Class} object. This includes public, protected, default
     * (package) access, and private methods, but excludes inherited methods.
     * The elements in the array returned are not sorted and are not in any
     * particular order.  This method returns an array of length 0 if the class
     * or interface declares no methods, or if this Class} object
     * represents a primitive type, an array class, or void.  The class
     * initialization method <clinit>} is not included in the
     * returned array. If the class declares multiple public member methods
     * with the same parameter types, they are all included in the returned
     * array.
     *
     * See <em>The Java Language Specification</em>, section 8.2.
     *
     * @return    the array of Method} objects representing all the
     * declared methods of this class
     * @exception  SecurityException
     *             If a security manager, <i>s</i>, is present and any of the
     *             following conditions is met:
     *
     *             <ul>
     *
     *             <li> invocation of
     *             {@link SecurityManager#checkMemberAccess
     *             s.checkMemberAccess(this, Member.DECLARED)} denies
     *             access to the declared methods within this class
     *
     *             <li> the caller's class loader is not the same as or an
     *             ancestor of the class loader for the current class and
     *             invocation of {@link SecurityManager#checkPackageAccess
     *             s.checkPackageAccess()} denies access to the package
     *             of this class
     *
     *             </ul>
     *
     * @since JDK1.1
     */
    @CallerSensitive
    public Method[] getDeclaredMethods() throws SecurityException {
        // be very careful not to change the stack depth of this
        // checkMemberAccess call for security reasons
        // see java.lang.SecurityManager.checkMemberAccess
        checkMemberAccess(Member.DECLARED, Reflection.getCallerClass(), true);
        return copyMethods(privateGetDeclaredMethods(false));
    }


    /**
     * Returns an array of Constructor} objects reflecting all the
     * constructors declared by the class represented by this
     * Class} object. These are public, protected, default
     * (package) access, and private constructors.  The elements in the array
     * returned are not sorted and are not in any particular order.  If the
     * class has a default constructor, it is included in the returned array.
     * This method returns an array of length 0 if this Class}
     * object represents an interface, a primitive type, an array class, or
     * void.
     *
     * See <em>The Java Language Specification</em>, section 8.2.
     *
     * @return    the array of Constructor} objects representing all the
     * declared constructors of this class
     * @exception  SecurityException
     *             If a security manager, <i>s</i>, is present and any of the
     *             following conditions is met:
     *
     *             <ul>
     *
     *             <li> invocation of
     *             {@link SecurityManager#checkMemberAccess
     *             s.checkMemberAccess(this, Member.DECLARED)} denies
     *             access to the declared constructors within this class
     *
     *             <li> the caller's class loader is not the same as or an
     *             ancestor of the class loader for the current class and
     *             invocation of {@link SecurityManager#checkPackageAccess
     *             s.checkPackageAccess()} denies access to the package
     *             of this class
     *
     *             </ul>
     *
     * @since JDK1.1
     */
    @CallerSensitive
    public Constructor<?>[] getDeclaredConstructors() throws SecurityException {
        // be very careful not to change the stack depth of this
        // checkMemberAccess call for security reasons
        // see java.lang.SecurityManager.checkMemberAccess
        checkMemberAccess(Member.DECLARED, Reflection.getCallerClass(), true);
        return copyConstructors(privateGetDeclaredConstructors(false));
    }


    /**
     * Returns a Field} object that reflects the specified declared
     * field of the class or interface represented by this Class}
     * object. The name} parameter is a String} that
     * specifies the simple name of the desired field.  Note that this method
     * will not reflect the length} field of an array class.
     *
     * @param name the name of the field
     * @return the Field} object for the specified field in this
     * class
     * @exception NoSuchFieldException if a field with the specified name is
     *              not found.
     * @exception NullPointerException if name} is null}
     * @exception  SecurityException
     *             If a security manager, <i>s</i>, is present and any of the
     *             following conditions is met:
     *
     *             <ul>
     *
     *             <li> invocation of
     *             {@link SecurityManager#checkMemberAccess
     *             s.checkMemberAccess(this, Member.DECLARED)} denies
     *             access to the declared field
     *
     *             <li> the caller's class loader is not the same as or an
     *             ancestor of the class loader for the current class and
     *             invocation of {@link SecurityManager#checkPackageAccess
     *             s.checkPackageAccess()} denies access to the package
     *             of this class
     *
     *             </ul>
     *
     * @since JDK1.1
     */
    @CallerSensitive
    public Field getDeclaredField(String name)
        throws NoSuchFieldException, SecurityException {
        // be very careful not to change the stack depth of this
        // checkMemberAccess call for security reasons
        // see java.lang.SecurityManager.checkMemberAccess
        checkMemberAccess(Member.DECLARED, Reflection.getCallerClass(), true);
        Field field = searchFields(privateGetDeclaredFields(false), name);
        if (field == null) {
            throw new NoSuchFieldException(name);
        }
        return field;
    }


    /**
     * Returns a Method object that reflects the specified declared method of the class or interface represented by this Class object.
     * The name parameter is a String that specifies the simple name of the desired method, and the parameterTypes parameter is an array of
     * Class objects that identify the method's formal parameter types, in declared order.
     * If more than one method with the same parameter types is declared in a class, and one of these methods has a return type that is more
     * specific than any of the others, that method is returned; otherwise one of the methods is chosen arbitrarily.
     *
     * If the name is "init" or "clinit", a NoSuchMethodException is raised.
     *
     * @param name the name of the method
     * @param parameterTypes the parameter array
     * @return the Method object for the method of this class
     * matching the specified name and parameters
     * @exception NoSuchMethodException if a matching method is not found.
     * @exception NullPointerException if name} is null}
     * @exception  SecurityException
     *             If a security manager, s, is present and any of the
     *             following conditions is met:
     *
     *             invocation of SecurityManager.checkMemberAccess s.checkMemberAccess(this, Member.DECLARED) denies access to the declared method
     *
     *             the caller's class loader is not the same as or an ancestor of the class loader for the current class and
     *             invocation of SecurityManager.checkPackageAccess s.checkPackageAccess() denies access to the package of this class
     *
     * @since JDK1.1
     */
    @CallerSensitive
    public Method getDeclaredMethod(String name, Class<?>... parameterTypes) throws NoSuchMethodException, SecurityException {
        // be very careful not to change the stack depth of this
        // checkMemberAccess call for security reasons
        // see java.lang.SecurityManager.checkMemberAccess
        checkMemberAccess(Member.DECLARED, Reflection.getCallerClass(), true);
        Method method = searchMethods(privateGetDeclaredMethods(false), name, parameterTypes);
        if (method == null) {
            throw new NoSuchMethodException(getName() + "." + name + argumentTypesToString(parameterTypes));
        }
        return method;
    }

    /**
     * Returns a Constructor} object that reflects the specified
     * constructor of the class or interface represented by this
     * Class} object.  The parameterTypes} parameter is
     * an array of Class} objects that identify the constructor's
     * formal parameter types, in declared order.
     *
     * If this Class} object represents an inner class
     * declared in a non-static context, the formal parameter types
     * include the explicit enclosing instance as the first parameter.
     *
     * @param parameterTypes the parameter array
     * @return    The Constructor} object for the constructor with the
     * specified parameter list
     * @exception NoSuchMethodException if a matching method is not found.
     * @exception  SecurityException
     *             If a security manager, <i>s</i>, is present and any of the
     *             following conditions is met:
     *
     *             <ul>
     *
     *             <li> invocation of
     *             {@link SecurityManager#checkMemberAccess
     *             s.checkMemberAccess(this, Member.DECLARED)} denies
     *             access to the declared constructor
     *
     *             <li> the caller's class loader is not the same as or an
     *             ancestor of the class loader for the current class and
     *             invocation of {@link SecurityManager#checkPackageAccess
     *             s.checkPackageAccess()} denies access to the package
     *             of this class
     *
     *             </ul>
     *
     * @since JDK1.1
     */
    @CallerSensitive
    public Constructor<T> getDeclaredConstructor(Class<?>... parameterTypes)
        throws NoSuchMethodException, SecurityException {
        // be very careful not to change the stack depth of this
        // checkMemberAccess call for security reasons
        // see java.lang.SecurityManager.checkMemberAccess
        checkMemberAccess(Member.DECLARED, Reflection.getCallerClass(), true);
        return getConstructor0(parameterTypes, Member.DECLARED);
    }

    /**
     * Finds a resource with a given name.  The rules for searching resources
     * associated with a given class are implemented by the defining
     * {@linkplain ClassLoader class loader} of the class.  This method
     * delegates to this object's class loader.  If this object was loaded by
     * the bootstrap class loader, the method delegates to {@link
     * ClassLoader#getSystemResourceAsStream}.
     *
     * Before delegation, an absolute resource name is constructed from the
     * given resource name using this algorithm:
     *
     * <ul>
     *
     * <li> If the name} begins with a '/'}
     * (<tt>'&#92;u002f'</tt>), then the absolute name of the resource is the
     * portion of the name} following the '/'}.
     *
     * <li> Otherwise, the absolute name is of the following form:
     *
     * <blockquote>
     *   modified_package_name/name}
     * </blockquote>
     *
     * Where the modified_package_name} is the package name of this
     * object with '/'} substituted for '.'}
     * (<tt>'&#92;u002e'</tt>).
     *
     * </ul>
     *
     * @param  name name of the desired resource
     * @return      A {@link java.io.InputStream} object or null} if
     *              no resource with this name is found
     * @throws  NullPointerException If name} is null}
     * @since  JDK1.1
     */
     public InputStream getResourceAsStream(String name) {
        name = resolveName(name);
        ClassLoader cl = getClassLoader0();
        if (cl==null) {
            // A system class.
            return ClassLoader.getSystemResourceAsStream(name);
        }
        return cl.getResourceAsStream(name);
    }

    /**
     * Finds a resource with a given name.  The rules for searching resources
     * associated with a given class are implemented by the defining
     * {@linkplain ClassLoader class loader} of the class.  This method
     * delegates to this object's class loader.  If this object was loaded by
     * the bootstrap class loader, the method delegates to {@link
     * ClassLoader#getSystemResource}.
     *
     * Before delegation, an absolute resource name is constructed from the
     * given resource name using this algorithm:
     *
     * <ul>
     *
     * <li> If the name} begins with a '/'}
     * (<tt>'&#92;u002f'</tt>), then the absolute name of the resource is the
     * portion of the name} following the '/'}.
     *
     * <li> Otherwise, the absolute name is of the following form:
     *
     * <blockquote>
     *   modified_package_name/name}
     * </blockquote>
     *
     * Where the modified_package_name} is the package name of this
     * object with '/'} substituted for '.'}
     * (<tt>'&#92;u002e'</tt>).
     *
     * </ul>
     *
     * @param  name name of the desired resource
     * @return      A  {@link java.net.URL} object or null} if no
     *              resource with this name is found
     * @since  JDK1.1
     */
    public java.net.URL getResource(String name) {
        name = resolveName(name);
        ClassLoader cl = getClassLoader0();
        if (cl==null) {
            // A system class.
            return ClassLoader.getSystemResource(name);
        }
        return cl.getResource(name);
    }



    /** protection domain returned when the internal domain is null */
    private static java.security.ProtectionDomain allPermDomain;


    /**
     * Returns the ProtectionDomain} of this class.  If there is a
     * security manager installed, this method first calls the security
     * manager's checkPermission} method with a
     * RuntimePermission("getProtectionDomain")} permission to
     * ensure it's ok to get the
     * ProtectionDomain}.
     *
     * @return the ProtectionDomain of this class
     *
     * @throws SecurityException
     *        if a security manager exists and its
     *        checkPermission} method doesn't allow
     *        getting the ProtectionDomain.
     *
     * @see java.security.ProtectionDomain
     * @see SecurityManager#checkPermission
     * @see java.lang.RuntimePermission
     * @since 1.2
     */
    public java.security.ProtectionDomain getProtectionDomain() {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(SecurityConstants.GET_PD_PERMISSION);
        }
        java.security.ProtectionDomain pd = getProtectionDomain0();
        if (pd == null) {
            if (allPermDomain == null) {
                java.security.Permissions perms =
                    new java.security.Permissions();
                perms.add(SecurityConstants.ALL_PERMISSION);
                allPermDomain =
                    new java.security.ProtectionDomain(null, perms);
            }
            pd = allPermDomain;
        }
        return pd;
    }


    /**
     * Returns the ProtectionDomain of this class.
     */
    private native java.security.ProtectionDomain getProtectionDomain0();


    /**
     * Set the ProtectionDomain for this class. Called by
     * ClassLoader.defineClass.
     */
    native void setProtectionDomain0(java.security.ProtectionDomain pd);


    /*
     * Return the Virtual Machine's Class object for the named
     * primitive type.
     */
    static native Class getPrimitiveClass(String name);

    private static boolean isCheckMemberAccessOverridden(SecurityManager smgr) {
        if (smgr.getClass() == SecurityManager.class) return false;

        Class<?>[] paramTypes = new Class<?>[] {Class.class, int.class};
        return smgr.getClass().getMethod0("checkMemberAccess", paramTypes).
                getDeclaringClass() != SecurityManager.class;
    }


    /*
     * Check if client is allowed to access members.  If access is denied,
     * throw a SecurityException.
     *
     * This method also enforces package access.
     *
     * Default policy: allow all clients access with normal Java access
     * control.
     */
    private void checkMemberAccess(int which, Class<?> caller, boolean checkProxyInterfaces) {
        final SecurityManager s = System.getSecurityManager();
        if (s != null) {
            final ClassLoader ccl = ClassLoader.getClassLoader(caller);
            final ClassLoader cl = getClassLoader0();
            if (!isCheckMemberAccessOverridden(s)) {
                // Inlined SecurityManager.checkMemberAccess
                if (which != Member.PUBLIC) {
                    if (ccl != cl) {
                        s.checkPermission(SecurityConstants.CHECK_MEMBER_ACCESS_PERMISSION);
                    }
                }
            } else {
                // Don't refactor; otherwise break the stack depth for
                // checkMemberAccess of subclasses of SecurityManager as specified.
                s.checkMemberAccess(this, which);
            }
            this.checkPackageAccess(ccl, checkProxyInterfaces);
        }
    }

    /*
     * Checks if a client loaded in ClassLoader ccl is allowed to access this
     * class under the current package access policy. If access is denied,
     * throw a SecurityException.
     */
    private void checkPackageAccess(final ClassLoader ccl, boolean checkProxyInterfaces) {
        final SecurityManager s = System.getSecurityManager();
        if (s != null) {
            final ClassLoader cl = getClassLoader0();
            if (ReflectUtil.needsPackageAccessCheck(ccl, cl)) {
                String name = this.getName();
                int i = name.lastIndexOf('.');
                if (i != -1) {
                    // skip the package access check on a proxy class in default proxy package
                    String pkg = name.substring(0, i);
                    if (!Proxy.isProxyClass(this) || ReflectUtil.isNonPublicProxyClass(this)) {
                        s.checkPackageAccess(pkg);
                    }
                }
            }
            // check package access on the proxy interfaces
            if (checkProxyInterfaces && Proxy.isProxyClass(this)) {
                ReflectUtil.checkProxyPackageAccess(ccl, this.getInterfaces());
            }
        }
    }

    /**
     * Add a package name prefix if the name is not absolute Remove leading "/"
     * if name is absolute
     */
    private String resolveName(String name) {
        if (name == null) {
            return name;
        }
        if (!name.startsWith("/")) {
            Class<?> c = this;
            while (c.isArray()) {
                c = c.getComponentType();
            }
            String baseName = c.getName();
            int index = baseName.lastIndexOf('.');
            if (index != -1) {
                name = baseName.substring(0, index).replace('.', '/')
                    +"/"+name;
            }
        } else {
            name = name.substring(1);
        }
        return name;
    }

    /**
     * Reflection support.
     */

    // Caches for certain reflective results
    private static boolean useCaches = true;
    private volatile transient SoftReference<Field[]> declaredFields;
    private volatile transient SoftReference<Field[]> publicFields;
    private volatile transient SoftReference<Method[]> declaredMethods;
    private volatile transient SoftReference<Method[]> publicMethods;
    private volatile transient SoftReference<Constructor<T>[]> declaredConstructors;
    private volatile transient SoftReference<Constructor<T>[]> publicConstructors;
    // Intermediate results for getFields and getMethods
    private volatile transient SoftReference<Field[]> declaredPublicFields;
    private volatile transient SoftReference<Method[]> declaredPublicMethods;

    // Incremented by the VM on each call to JVM TI RedefineClasses()
    // that redefines this class or a superclass.
    private volatile transient int classRedefinedCount = 0;

    // Value of classRedefinedCount when we last cleared the cached values
    // that are sensitive to class redefinition.
    private volatile transient int lastRedefinedCount = 0;

    // Clears cached values that might possibly have been obsoleted by
    // a class redefinition.
    private void clearCachesOnClassRedefinition() {
        if (lastRedefinedCount != classRedefinedCount) {
            declaredFields = publicFields = declaredPublicFields = null;
            declaredMethods = publicMethods = declaredPublicMethods = null;
            declaredConstructors = publicConstructors = null;
            annotations = declaredAnnotations = null;

            // Use of "volatile" (and synchronization by caller in the case
            // of annotations) ensures that no thread sees the update to
            // lastRedefinedCount before seeing the caches cleared.
            // We do not guard against brief windows during which multiple
            // threads might redundantly work to fill an empty cache.
            lastRedefinedCount = classRedefinedCount;
        }
    }

    // Generic signature handling
    private native String getGenericSignature();

    // Generic info repository; lazily initialized
    private transient ClassRepository genericInfo;

    // accessor for factory
    private GenericsFactory getFactory() {
        // create scope and factory
        return CoreReflectionFactory.make(this, ClassScope.make(this));
    }

    // accessor for generic info repository
    private ClassRepository getGenericInfo() {
        // lazily initialize repository if necessary
        if (genericInfo == null) {
            // create and cache generic info repository
            genericInfo = ClassRepository.make(getGenericSignature(),
                                               getFactory());
        }
        return genericInfo; //return cached repository
    }

    // Annotations handling
    private native byte[] getRawAnnotations();

    native ConstantPool getConstantPool();

    //
    //
    // java.lang.reflect.Field handling
    //
    //

    // Returns an array of "root" fields. These Field objects must NOT
    // be propagated to the outside world, but must instead be copied
    // via ReflectionFactory.copyField.
    private Field[] privateGetDeclaredFields(boolean publicOnly) {
        checkInitted();
        Field[] res = null;
        if (useCaches) {
            clearCachesOnClassRedefinition();
            if (publicOnly) {
                if (declaredPublicFields != null) {
                    res = declaredPublicFields.get();
                }
            } else {
                if (declaredFields != null) {
                    res = declaredFields.get();
                }
            }
            if (res != null) return res;
        }
        // No cached value available; request value from VM
        res = Reflection.filterFields(this, getDeclaredFields0(publicOnly));
        if (useCaches) {
            if (publicOnly) {
                declaredPublicFields = new SoftReference<>(res);
            } else {
                declaredFields = new SoftReference<>(res);
            }
        }
        return res;
    }

    // Returns an array of "root" fields. These Field objects must NOT
    // be propagated to the outside world, but must instead be copied
    // via ReflectionFactory.copyField.
    private Field[] privateGetPublicFields(Set<Class<?>> traversedInterfaces) {
        checkInitted();
        Field[] res = null;
        if (useCaches) {
            clearCachesOnClassRedefinition();
            if (publicFields != null) {
                res = publicFields.get();
            }
            if (res != null) return res;
        }

        // No cached value available; compute value recursively.
        // Traverse in correct order for getField().
        List<Field> fields = new ArrayList<>();
        if (traversedInterfaces == null) {
            traversedInterfaces = new HashSet<>();
        }

        // Local fields
        Field[] tmp = privateGetDeclaredFields(true);
        addAll(fields, tmp);

        // Direct superinterfaces, recursively
        for (Class<?> c : getInterfaces()) {
            if (!traversedInterfaces.contains(c)) {
                traversedInterfaces.add(c);
                addAll(fields, c.privateGetPublicFields(traversedInterfaces));
            }
        }

        // Direct superclass, recursively
        if (!isInterface()) {
            Class<?> c = getSuperclass();
            if (c != null) {
                addAll(fields, c.privateGetPublicFields(traversedInterfaces));
            }
        }

        res = new Field[fields.size()];
        fields.toArray(res);
        if (useCaches) {
            publicFields = new SoftReference<>(res);
        }
        return res;
    }

    private static void addAll(Collection<Field> c, Field[] o) {
        for (int i = 0; i < o.length; i++) {
            c.add(o[i]);
        }
    }


    //
    //
    // java.lang.reflect.Constructor handling
    //
    //

    // Returns an array of "root" constructors. These Constructor
    // objects must NOT be propagated to the outside world, but must
    // instead be copied via ReflectionFactory.copyConstructor.
    private Constructor<T>[] privateGetDeclaredConstructors(boolean publicOnly) {
        checkInitted();
        Constructor<T>[] res = null;
        if (useCaches) {
            clearCachesOnClassRedefinition();
            if (publicOnly) {
                if (publicConstructors != null) {
                    res = publicConstructors.get();
                }
            } else {
                if (declaredConstructors != null) {
                    res = declaredConstructors.get();
                }
            }
            if (res != null) return res;
        }
        // No cached value available; request value from VM
        if (isInterface()) {
            res = new Constructor[0];
        } else {
            res = getDeclaredConstructors0(publicOnly);
        }
        if (useCaches) {
            if (publicOnly) {
                publicConstructors = new SoftReference<>(res);
            } else {
                declaredConstructors = new SoftReference<>(res);
            }
        }
        return res;
    }

    //
    //
    // java.lang.reflect.Method handling
    //
    //

    // Returns an array of "root" methods. These Method objects must NOT
    // be propagated to the outside world, but must instead be copied
    // via ReflectionFactory.copyMethod.
    private Method[] privateGetDeclaredMethods(boolean publicOnly) {
        checkInitted();
        Method[] res = null;
        if (useCaches) {
            clearCachesOnClassRedefinition();
            if (publicOnly) {
                if (declaredPublicMethods != null) {
                    res = declaredPublicMethods.get();
                }
            } else {
                if (declaredMethods != null) {
                    res = declaredMethods.get();
                }
            }
            if (res != null) return res;
        }
        // No cached value available; request value from VM
        res = Reflection.filterMethods(this, getDeclaredMethods0(publicOnly));
        if (useCaches) {
            if (publicOnly) {
                declaredPublicMethods = new SoftReference<>(res);
            } else {
                declaredMethods = new SoftReference<>(res);
            }
        }
        return res;
    }

    static class MethodArray {
        private Method[] methods;
        private int length;

        MethodArray() {
            methods = new Method[20];
            length = 0;
        }

        void add(Method m) {
            if (length == methods.length) {
                methods = Arrays.copyOf(methods, 2 * methods.length);
            }
            methods[length++] = m;
        }

        void addAll(Method[] ma) {
            for (int i = 0; i < ma.length; i++) {
                add(ma[i]);
            }
        }

        void addAll(MethodArray ma) {
            for (int i = 0; i < ma.length(); i++) {
                add(ma.get(i));
            }
        }

        void addIfNotPresent(Method newMethod) {
            for (int i = 0; i < length; i++) {
                Method m = methods[i];
                if (m == newMethod || (m != null && m.equals(newMethod))) {
                    return;
                }
            }
            add(newMethod);
        }

        void addAllIfNotPresent(MethodArray newMethods) {
            for (int i = 0; i < newMethods.length(); i++) {
                Method m = newMethods.get(i);
                if (m != null) {
                    addIfNotPresent(m);
                }
            }
        }

        int length() {
            return length;
        }

        Method get(int i) {
            return methods[i];
        }

        void removeByNameAndSignature(Method toRemove) {
            for (int i = 0; i < length; i++) {
                Method m = methods[i];
                if (m != null &&
                    m.getReturnType() == toRemove.getReturnType() &&
                    m.getName() == toRemove.getName() &&
                    arrayContentsEq(m.getParameterTypes(),
                                    toRemove.getParameterTypes())) {
                    methods[i] = null;
                }
            }
        }

        void compactAndTrim() {
            int newPos = 0;
            // Get rid of null slots
            for (int pos = 0; pos < length; pos++) {
                Method m = methods[pos];
                if (m != null) {
                    if (pos != newPos) {
                        methods[newPos] = m;
                    }
                    newPos++;
                }
            }
            if (newPos != methods.length) {
                methods = Arrays.copyOf(methods, newPos);
            }
        }

        Method[] getArray() {
            return methods;
        }
    }


    // Returns an array of "root" methods. These Method objects must NOT
    // be propagated to the outside world, but must instead be copied
    // via ReflectionFactory.copyMethod.
    private Method[] privateGetPublicMethods() {
        checkInitted();
        Method[] res = null;
        if (useCaches) {
            clearCachesOnClassRedefinition();
            if (publicMethods != null) {
                res = publicMethods.get();
            }
            if (res != null) return res;
        }

        // No cached value available; compute value recursively.
        // Start by fetching public declared methods
        MethodArray methods = new MethodArray();
        {
            Method[] tmp = privateGetDeclaredMethods(true);
            methods.addAll(tmp);
        }
        // Now recur over superclass and direct superinterfaces.
        // Go over superinterfaces first so we can more easily filter
        // out concrete implementations inherited from superclasses at
        // the end.
        MethodArray inheritedMethods = new MethodArray();
        Class<?>[] interfaces = getInterfaces();
        for (int i = 0; i < interfaces.length; i++) {
            inheritedMethods.addAll(interfaces[i].privateGetPublicMethods());
        }
        if (!isInterface()) {
            Class<?> c = getSuperclass();
            if (c != null) {
                MethodArray supers = new MethodArray();
                supers.addAll(c.privateGetPublicMethods());
                // Filter out concrete implementations of any
                // interface methods
                for (int i = 0; i < supers.length(); i++) {
                    Method m = supers.get(i);
                    if (m != null && !Modifier.isAbstract(m.getModifiers())) {
                        inheritedMethods.removeByNameAndSignature(m);
                    }
                }
                // Insert superclass's inherited methods before
                // superinterfaces' to satisfy getMethod's search
                // order
                supers.addAll(inheritedMethods);
                inheritedMethods = supers;
            }
        }
        // Filter out all local methods from inherited ones
        for (int i = 0; i < methods.length(); i++) {
            Method m = methods.get(i);
            inheritedMethods.removeByNameAndSignature(m);
        }
        methods.addAllIfNotPresent(inheritedMethods);
        methods.compactAndTrim();
        res = methods.getArray();
        if (useCaches) {
            publicMethods = new SoftReference<>(res);
        }
        return res;
    }


    //
    // Helpers for fetchers of one field, method, or constructor
    //

    private Field searchFields(Field[] fields, String name) {
        String internedName = name.intern();
        for (int i = 0; i < fields.length; i++) {
            if (fields[i].getName() == internedName) {
                return getReflectionFactory().copyField(fields[i]);
            }
        }
        return null;
    }

    private Field getField0(String name) throws NoSuchFieldException {
        // Note: the intent is that the search algorithm this routine
        // uses be equivalent to the ordering imposed by
        // privateGetPublicFields(). It fetches only the declared
        // public fields for each class, however, to reduce the number
        // of Field objects which have to be created for the common
        // case where the field being requested is declared in the
        // class which is being queried.
        Field res = null;
        // Search declared public fields
        if ((res = searchFields(privateGetDeclaredFields(true), name)) != null) {
            return res;
        }
        // Direct superinterfaces, recursively
        Class<?>[] interfaces = getInterfaces();
        for (int i = 0; i < interfaces.length; i++) {
            Class<?> c = interfaces[i];
            if ((res = c.getField0(name)) != null) {
                return res;
            }
        }
        // Direct superclass, recursively
        if (!isInterface()) {
            Class<?> c = getSuperclass();
            if (c != null) {
                if ((res = c.getField0(name)) != null) {
                    return res;
                }
            }
        }
        return null;
    }

    private static Method searchMethods(Method[] methods,
                                        String name,
                                        Class<?>[] parameterTypes)
    {
        Method res = null;
        String internedName = name.intern();
        for (int i = 0; i < methods.length; i++) {
            Method m = methods[i];
            if (m.getName() == internedName
                && arrayContentsEq(parameterTypes, m.getParameterTypes())
                && (res == null
                    || res.getReturnType().isAssignableFrom(m.getReturnType())))
                res = m;
        }

        return (res == null ? res : getReflectionFactory().copyMethod(res));
    }


    private Method getMethod0(String name, Class<?>[] parameterTypes) {
        // Note: the intent is that the search algorithm this routine
        // uses be equivalent to the ordering imposed by
        // privateGetPublicMethods(). It fetches only the declared
        // public methods for each class, however, to reduce the
        // number of Method objects which have to be created for the
        // common case where the method being requested is declared in
        // the class which is being queried.
        Method res = null;
        // Search declared public methods
        if ((res = searchMethods(privateGetDeclaredMethods(true),
                                 name,
                                 parameterTypes)) != null) {
            return res;
        }
        // Search superclass's methods
        if (!isInterface()) {
            Class<? super T> c = getSuperclass();
            if (c != null) {
                if ((res = c.getMethod0(name, parameterTypes)) != null) {
                    return res;
                }
            }
        }
        // Search superinterfaces' methods
        Class<?>[] interfaces = getInterfaces();
        for (int i = 0; i < interfaces.length; i++) {
            Class<?> c = interfaces[i];
            if ((res = c.getMethod0(name, parameterTypes)) != null) {
                return res;
            }
        }
        // Not found
        return null;
    }

    private Constructor<T> getConstructor0(Class<?>[] parameterTypes,
                                        int which) throws NoSuchMethodException
    {
        Constructor<T>[] constructors = privateGetDeclaredConstructors((which == Member.PUBLIC));
        for (Constructor<T> constructor : constructors) {
            if (arrayContentsEq(parameterTypes,
                                constructor.getParameterTypes())) {
                return getReflectionFactory().copyConstructor(constructor);
            }
        }
        throw new NoSuchMethodException(getName() + ".<init>" + argumentTypesToString(parameterTypes));
    }

    //
    // Other helpers and base implementation
    //

    private static boolean arrayContentsEq(Object[] a1, Object[] a2) {
        if (a1 == null) {
            return a2 == null || a2.length == 0;
        }

        if (a2 == null) {
            return a1.length == 0;
        }

        if (a1.length != a2.length) {
            return false;
        }

        for (int i = 0; i < a1.length; i++) {
            if (a1[i] != a2[i]) {
                return false;
            }
        }

        return true;
    }

    private static Field[] copyFields(Field[] arg) {
        Field[] out = new Field[arg.length];
        ReflectionFactory fact = getReflectionFactory();
        for (int i = 0; i < arg.length; i++) {
            out[i] = fact.copyField(arg[i]);
        }
        return out;
    }

    private static Method[] copyMethods(Method[] arg) {
        Method[] out = new Method[arg.length];
        ReflectionFactory fact = getReflectionFactory();
        for (int i = 0; i < arg.length; i++) {
            out[i] = fact.copyMethod(arg[i]);
        }
        return out;
    }

    private static <U> Constructor<U>[] copyConstructors(Constructor<U>[] arg) {
        Constructor<U>[] out = arg.clone();
        ReflectionFactory fact = getReflectionFactory();
        for (int i = 0; i < out.length; i++) {
            out[i] = fact.copyConstructor(out[i]);
        }
        return out;
    }

    private native Field[]       getDeclaredFields0(boolean publicOnly);
    private native Method[]      getDeclaredMethods0(boolean publicOnly);
    private native Constructor<T>[] getDeclaredConstructors0(boolean publicOnly);
    private native Class<?>[]   getDeclaredClasses0();

    private static String        argumentTypesToString(Class<?>[] argTypes) {
        StringBuilder buf = new StringBuilder();
        buf.append("(");
        if (argTypes != null) {
            for (int i = 0; i < argTypes.length; i++) {
                if (i > 0) {
                    buf.append(", ");
                }
                Class<?> c = argTypes[i];
                buf.append((c == null) ? "null" : c.getName());
            }
        }
        buf.append(")");
        return buf.toString();
    }

    /** use serialVersionUID from JDK 1.1 for interoperability */
    private static final long serialVersionUID = 3206093459760846163L;


    /**
     * Class Class is special cased within the Serialization Stream Protocol.
     *
     * A Class instance is written initially into an ObjectOutputStream in the following format:
     *
     *      TC_CLASS ClassDescriptor
     *      A ClassDescriptor is a special cased serialization of a java.io.ObjectStreamClass instance.
     *
     * A new handle is generated for the initial time the class descriptor is written into the stream. Future references to the class descriptor
     * are written as references to the initial class descriptor instance.
     *
     * @see java.io.ObjectStreamClass
     */
    private static final ObjectStreamField[] serialPersistentFields =
        new ObjectStreamField[0];


    /**
     * Returns the assertion status that would be assigned to this
     * class if it were to be initialized at the time this method is invoked.
     * If this class has had its assertion status set, the most recent
     * setting will be returned; otherwise, if any package default assertion
     * status pertains to this class, the most recent setting for the most
     * specific pertinent package default assertion status is returned;
     * otherwise, if this class is not a system class (i.e., it has a
     * class loader) its class loader's default assertion status is returned;
     * otherwise, the system class default assertion status is returned.
     * 
     * Few programmers will have any need for this method; it is provided
     * for the benefit of the JRE itself.  (It allows a class to determine at
     * the time that it is initialized whether assertions should be enabled.)
     * Note that this method is not guaranteed to return the actual
     * assertion status that was (or will be) associated with the specified
     * class when it was (or will be) initialized.
     *
     * @return the desired assertion status of the specified class.
     * @see    java.lang.ClassLoader#setClassAssertionStatus
     * @see    java.lang.ClassLoader#setPackageAssertionStatus
     * @see    java.lang.ClassLoader#setDefaultAssertionStatus
     * @since  1.4
     */
    public boolean desiredAssertionStatus() {
        ClassLoader loader = getClassLoader();
        // If the loader is null this is a system class, so ask the VM
        if (loader == null)
            return desiredAssertionStatus0(this);

        // If the classloader has been initialized with the assertion
        // directives, ask it. Otherwise, ask the VM.
        synchronized(loader.assertionLock) {
            if (loader.classAssertionStatus != null) {
                return loader.desiredAssertionStatus(getName());
            }
        }
        return desiredAssertionStatus0(this);
    }

    // Retrieves the desired assertion status of this class from the VM
    private static native boolean desiredAssertionStatus0(Class<?> clazz);

    /**
     * Returns true if and only if this class was declared as an enum in the source code.
     *
     * @return true if and only if this class was declared as an enum in the source code
     * @since 1.5
     */
    public boolean isEnum() {
        // An enum must both directly extend java.lang.Enum and have
        // the ENUM bit set; classes for specialized enum constants
        // don't do the former.
        return (this.getModifiers() & ENUM) != 0 &&
        this.getSuperclass() == java.lang.Enum.class;
    }

    // Fetches the factory for reflective objects
    private static ReflectionFactory getReflectionFactory() {
        if (reflectionFactory == null) {
            reflectionFactory =
                java.security.AccessController.doPrivileged
                    (new sun.reflect.ReflectionFactory.GetReflectionFactoryAction());
        }
        return reflectionFactory;
    }
    private static ReflectionFactory reflectionFactory;

    // To be able to query system properties as soon as they're available
    private static boolean initted = false;
    private static void checkInitted() {
        if (initted) return;
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
                public Void run() {
                    // Tests to ensure the system properties table is fully
                    // initialized. This is needed because reflection code is
                    // called very early in the initialization process (before
                    // command-line arguments have been parsed and therefore
                    // these user-settable properties installed.) We assume that
                    // if System.out is non-null then the System class has been
                    // fully initialized and that the bulk of the startup code
                    // has been run.

                    if (System.out == null) {
                        // java.lang.System not yet fully initialized
                        return null;
                    }

                    String val =
                        System.getProperty("sun.reflect.noCaches");
                    if (val != null && val.equals("true")) {
                        useCaches = false;
                    }

                    initted = true;
                    return null;
                }
            });
    }

    /**
     * Returns the elements of this enum class or null if this Class object does not represent an enum type.
     *
     * @return an array containing the values comprising the enum class represented by this Class object in the order they're declared, or null
     *         if this Class object does not represent an enum type
     * @since 1.5
     */
    public T[] getEnumConstants() {
        T[] values = getEnumConstantsShared();
        return (values != null) ? values.clone() : null;
    }

    /**
     * Returns the elements of this enum class or null if this Class object does not represent an enum type; identical to getEnumConstants
     * except that the result is uncloned, cached, and shared by all callers.
     */
    T[] getEnumConstantsShared() {
        if (enumConstants == null) {
            if (!isEnum()) return null;
            try {
                final Method values = getMethod("values");
                java.security.AccessController.doPrivileged(
                    new java.security.PrivilegedAction<Void>() {
                        public Void run() {
                                values.setAccessible(true);
                                return null;
                            }
                        });
                enumConstants = (T[])values.invoke(null);
            }
            // These can happen when users concoct enum-like classes
            // that don't comply with the enum spec.
            catch (InvocationTargetException ex) { return null; }
            catch (NoSuchMethodException ex) { return null; }
            catch (IllegalAccessException ex) { return null; }
        }
        return enumConstants;
    }
    private volatile transient T[] enumConstants = null;

    /**
     * Returns a map from simple name to enum constant. This package-private method is used internally by Enum to implement
     * public static <T extends Enum<T>> T valueOf(Class<T>, String) efficiently. Note that the map is returned by this method
     * is created lazily on first use. Typically it won't ever get created.
     */
    Map<String, T> enumConstantDirectory() {
        if (enumConstantDirectory == null) {
            T[] universe = getEnumConstantsShared();
            if (universe == null)
                throw new IllegalArgumentException(
                    getName() + " is not an enum type");
            Map<String, T> m = new HashMap<>(2 * universe.length);
            for (T constant : universe)
                m.put(((Enum<?>)constant).name(), constant);
            enumConstantDirectory = m;
        }
        return enumConstantDirectory;
    }
    private volatile transient Map<String, T> enumConstantDirectory = null;

    /**
     * Casts an object to the class or interface represented by this Class object.
     *
     * @param obj the object to be cast
     * @return the object after casting, or null if obj is null
     *
     * @throws ClassCastException if the object is not null and is not assignable to the type T.
     *
     * @since 1.5
     */
    public T cast(Object obj) {
        if (obj != null && !isInstance(obj))
            throw new ClassCastException(cannotCastMsg(obj));
        return (T) obj;
    }

    private String cannotCastMsg(Object obj) {
        return "Cannot cast " + obj.getClass().getName() + " to " + getName();
    }

    /**
     * Casts this Class object to represent a subclass of the class
     * represented by the specified class object.  Checks that that the cast
     * is valid, and throws a ClassCastException} if it is not.  If
     * this method succeeds, it always returns a reference to this class object.
     *
     * This method is useful when a client needs to "narrow" the type of
     * a Class} object to pass it to an API that restricts the
     * Class} objects that it is willing to accept.  A cast would
     * generate a compile-time warning, as the correctness of the cast
     * could not be checked at runtime (because generic types are implemented
     * by erasure).
     *
     * @return this Class} object, cast to represent a subclass of
     *    the specified class object.
     * @throws ClassCastException if this Class} object does not
     *    represent a subclass of the specified class (here "subclass" includes
     *    the class itself).
     * @since 1.5
     */
    public <U> Class<? extends U> asSubclass(Class<U> clazz) {
        if (clazz.isAssignableFrom(this))
            return (Class<? extends U>) this;
        else
            throw new ClassCastException(this.toString());
    }

    /**
     * @throws NullPointerException {@inheritDoc}
     * @since 1.5
     */
    public <A extends Annotation> A getAnnotation(Class<A> annotationClass) {
        if (annotationClass == null)
            throw new NullPointerException();

        initAnnotationsIfNecessary();
        return (A) annotations.get(annotationClass);
    }

    /**
     * @throws NullPointerException {@inheritDoc}
     * @since 1.5
     */
    public boolean isAnnotationPresent(
        Class<? extends Annotation> annotationClass) {
        if (annotationClass == null)
            throw new NullPointerException();

        return getAnnotation(annotationClass) != null;
    }


    /**
     * @since 1.5
     */
    public Annotation[] getAnnotations() {
        initAnnotationsIfNecessary();
        return AnnotationParser.toArray(annotations);
    }

    /**
     * @since 1.5
     */
    public Annotation[] getDeclaredAnnotations()  {
        initAnnotationsIfNecessary();
        return AnnotationParser.toArray(declaredAnnotations);
    }

    // Annotations cache
    private transient Map<Class<? extends Annotation>, Annotation> annotations;
    private transient Map<Class<? extends Annotation>, Annotation> declaredAnnotations;

    private synchronized void initAnnotationsIfNecessary() {
        clearCachesOnClassRedefinition();
        if (annotations != null)
            return;
        declaredAnnotations = AnnotationParser.parseAnnotations(
            getRawAnnotations(), getConstantPool(), this);
        Class<?> superClass = getSuperclass();
        if (superClass == null) {
            annotations = declaredAnnotations;
        } else {
            annotations = new HashMap<>();
            superClass.initAnnotationsIfNecessary();
            for (Map.Entry<Class<? extends Annotation>, Annotation> e : superClass.annotations.entrySet()) {
                Class<? extends Annotation> annotationClass = e.getKey();
                if (AnnotationType.getInstance(annotationClass).isInherited())
                    annotations.put(annotationClass, e.getValue());
            }
            annotations.putAll(declaredAnnotations);
        }
    }

    // Annotation types cache their internal (AnnotationType) form

    private AnnotationType annotationType;

    void setAnnotationType(AnnotationType type) {
        annotationType = type;
    }

    AnnotationType getAnnotationType() {
        return annotationType;
    }

    /* Backing store of user-defined values pertaining to this class.
     * Maintained by the ClassValue class.
     */
    transient ClassValue.ClassValueMap classValueMap;
}
