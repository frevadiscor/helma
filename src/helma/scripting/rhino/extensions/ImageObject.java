/*
 * Helma License Notice
 *
 * The contents of this file are subject to the Helma License
 * Version 2.0 (the "License"). You may not use this file except in
 * compliance with the License. A copy of the License is available at
 * http://adele.helma.org/download/helma/license.txt
 *
 * Copyright 1998-2003 Helma Software. All Rights Reserved.
 *
 * $RCSfile$
 * $Author$
 * $Revision$
 * $Date$
 */

package helma.scripting.rhino.extensions;

import helma.image.*;
import helma.util.MimePart;

import java.awt.image.*;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.FunctionObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.NativeJavaArray;
import org.mozilla.javascript.NativeJavaObject;
import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Wrapper;

import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.net.MalformedURLException;

/**
 * Extension to provide Helma with Image processing features.
 */
public class ImageObject {

    /**
     * Called by the evaluator after the extension is loaded.
     */
    public static void init(Scriptable scope) {
        Method[] methods = ImageObject.class.getDeclaredMethods();
        Member ctorMember = null;
        for (int i=0; i<methods.length; i++) {
            if ("imageCtor".equals(methods[i].getName())) {
                ctorMember = methods[i];
                break;
            }
        }
        FunctionObject ctor = new FunctionObject("Image", ctorMember, scope);
        ScriptableObject.defineProperty(scope, "Image", ctor, ScriptableObject.DONTENUM);
        ctor.put("getInfo", ctor, new GetInfo());
    }

    public static Object imageCtor (Context cx, Object[] args,
                Function ctorObj, boolean inNewExpr) {

        Object img = null;

        try {

            ImageGenerator generator = ImageGenerator.getInstance();

            if (args.length == 1) {
                if (args[0] instanceof NativeJavaArray) {
                    Object array = ((NativeJavaArray) args[0]).unwrap();
                    if (array instanceof byte[]) {
                        img = generator.createImage((byte[]) array);
                    }
                } else if (args[0] instanceof byte[]) {
                    img = generator.createImage((byte[]) args[0]);
                } else if (args[0] instanceof String) {
                    // the string could either be a url or a local filename, let's try both:
                    String str = args[0].toString();
                    try {
                        URL url = new URL(str);
                        img = generator.createImage(url);
                    } catch (MalformedURLException e) {
                        // try the local file now:
                        img = generator.createImage(str);
                    }
                } else if (args[0] instanceof NativeJavaObject) {
                    Object arg = ((NativeJavaObject) args[0]).unwrap();
                    if (arg instanceof MimePart) {
                        img = generator.createImage(((MimePart) arg).getContent());
                    }
                }
            } else if (args.length == 2) {
                if (args[0] instanceof Number &&
                                   args[1] instanceof Number) {
                    img = generator.createImage(((Number) args[0]).intValue(),
                                                          ((Number) args[1]).intValue());
                } else if (args[0] instanceof NativeJavaObject &&
                        args[1] instanceof NativeJavaObject) {
                    // create a new image from an existing one and an image filter
                    Object wrapper = ((NativeJavaObject) args[0]).unwrap();
                    if (wrapper instanceof ImageWrapper) {
                        Object filter = ((NativeJavaObject) args[1]).unwrap();
                        if (filter instanceof ImageFilter)
                            img = generator.createImage((ImageWrapper) wrapper, (ImageFilter) filter);
                        else if (filter instanceof BufferedImageOp)
                            img = generator.createImage((ImageWrapper) wrapper, (BufferedImageOp) filter);
                    }
                }
            }
        } catch (IOException iox) {
            throw new RuntimeException("Error creating Image: " + iox);
        }

        if (img == null) {
            switch (args.length) {
                case 0:
                    throw new RuntimeException("Error creating Image: Called without arguments");
                case 1:
                    throw new RuntimeException("Error creating Image from " + args[0]);
                case 2:
                    throw new RuntimeException("Error creating Image from " + args[0] + ", " + args[1]);
                default:
                    throw new RuntimeException("Error creating Image: Wrong number of arguments");
            }
        }

        return Context.toObject(img, ctorObj.getParentScope());
    }

    static class GetInfo extends BaseFunction {
        public Object call(Context cx, Scriptable scope,
                           Scriptable thisObj, Object[] args) {
            if (args.length != 1) {
                throw new IllegalArgumentException("Image.getInfo() expects one argument");
            }

            Object arg = args[0];
            InputStream in = null;
            ImageInfo info = new ImageInfo();
            Object ret = null;
    
            try {
                if (arg instanceof Wrapper) {
                    arg = ((Wrapper) arg).unwrap();
                }
    
                if (arg instanceof InputStream) {
                    in = (InputStream) arg;
                } else if (arg instanceof byte[]) {
                    in = new ByteArrayInputStream((byte[]) arg);
                } else if (arg instanceof File) {
                    in = new FileInputStream((File) arg);
                } else if (arg instanceof File) {
                    in = new FileInputStream((File) arg);
                } else if (arg instanceof FileObject) {
                    in = new FileInputStream(((FileObject)arg).getFile());
                } else if (arg instanceof String) {
                    String str = (String) arg;
                    // try to interpret argument as URL if it contains a colon,
                    // otherwise or if URL is malformed interpret as file name.
                    if (str.indexOf(":") > -1) {
                        try {
                            URL url = new URL(str);
                            in = url.openStream();
                        } catch (MalformedURLException mux) {
                            in = new FileInputStream(str);
                        }
                    } else {
                        in = new FileInputStream(str);
                    }
                }
    
                if (in == null) {
                    String msg = "Unrecognized argument in Image.getInfo(): ";
                    msg += arg == null ? "null" : arg.getClass().toString();
                    throw new IllegalArgumentException(msg);
                }
    
                info.setInput(in);
                if (info.check()) {
                    ret = Context.toObject(info, scope);
                }
    
            } catch (IOException e) {
                // do nothing, returns null later
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException ee) {
                    }
                }
            }
            return ret;
        }
    }
}
