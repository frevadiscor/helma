// Template.java
// Copyright (c) Hannes Walln�fer 1998-2000
 
package helma.scripting;

import java.io.*;
import java.util.Vector;
import java.util.Iterator;
import java.util.StringTokenizer;
import helma.framework.*;
import helma.framework.core.*;
// import FESI.Data.*;
// import FESI.Exceptions.*;


/**
 * This represents a Helma template, i.e. a file with the extension .hsp
 * (Helma server page) that contains both parts that are to be evaluated
 * as EcmaScript and parts that are to be delivered to the client as-is.
 * Internally, templates are regular functions. 
 * Helma templates are callable via URL, but this is just a leftover from the
 * days when there were no .hac (action) files. The recommended way
 * now is to have a .hac file with all the logic which in turn calls one or more
 * template files to do the formatting.
 */

public class Template extends ActionFile {


    public Template (File file, String name, Prototype proto) {
	super (file, name, proto);
	functionName = name;
    }

    public Template (String content, String name, Prototype proto) {
	super (content, name, proto);
	functionName = name;
    }


    public String getContent () {

	Vector partBuffer = new Vector ();
	int l = content.length ();
	if (l == 0)
	    return "";
	char cnt[] = new char[l];
	content.getChars (0, l, cnt, 0);

	// if last charackter is whitespace, swallow it. this is necessary for some inner templates to look ok.
	if (Character.isWhitespace (cnt[l-1]))
	    l -= 1;

	int lastIdx = 0;
	for (int i = 0; i < l-1; i++) {
	    if (cnt[i] == '<' && cnt[i+1] == '%') {
	        int j = i+2;
	        while (j < l-1 && (cnt[j] != '%' || cnt[j+1] != '>')) {
	            j++;
	        }
	        if (j > i+2) {
	            if (i - lastIdx > 0)
	                partBuffer.addElement (new Part (this, new String (cnt, lastIdx, i - lastIdx), true));
	            String script = new String (cnt, i+2, (j-i)-2);
	            partBuffer.addElement (new Part (this, script, false));
	            lastIdx = j+2;
	        }
	        i = j+1;
	    }
	}
	if (lastIdx < l)
	    partBuffer.addElement (new Part (this, new String (cnt, lastIdx, l - lastIdx), true));

	StringBuffer templateBody = new StringBuffer ();
	int nparts = partBuffer.size();

	for (int k = 0; k < nparts; k++) {
	    Part nextPart = (Part) partBuffer.elementAt (k);

	    if (nextPart.isStatic || nextPart.content.trim ().startsWith ("=")) {
	        // check for <%= ... %> statements
	        if (!nextPart.isStatic) {
	            nextPart.content = nextPart.content.trim ().substring (1).trim ();
	            // cut trailing ";"
	            while (nextPart.content.endsWith (";"))
	                nextPart.content = nextPart.content.substring (0, nextPart.content.length()-1);
	        }

	        StringTokenizer st = new StringTokenizer (nextPart.content, "\r\n", true);
	        String nextLine = st.hasMoreTokens () ? st.nextToken () : null;

	        // count newLines we "swallow", see explanation below
	        int newLineCount = 0;

	        templateBody.append ("res.write (");
	        if (nextPart.isStatic) {
	            templateBody.append ("\"");
	        }

	        while (nextLine != null) {

	            if ("\n".equals (nextLine)) {
	                // append a CRLF
	                newLineCount++;
	                templateBody.append ("\\r\\n");
	            } else if (!"\r".equals (nextLine)) try {
	                StringReader lineReader = new StringReader (nextLine);
	                int c = lineReader.read ();
	                while (c > -1) {
	                    if (nextPart.isStatic && ((char)c == '"' || (char)c == '\\')) {
	                        templateBody.append ('\\');
	                    }
	                    templateBody.append ((char) c);
	                    c = lineReader.read ();
	                }
	            } catch (IOException srx) {}

	            nextLine = st.hasMoreTokens () ? st.nextToken () : null;

	        }

	        if (nextPart.isStatic) {
	            templateBody.append ("\"");
	        }

	        templateBody.append ("); ");

	        // append the number of lines we have "swallowed" into
	        // one write statement, so error messages will *approximately*
	        // give correct line numbers.
	        for (int i=0; i<newLineCount; i++) {
	                templateBody.append ("\r\n");
	        }

	    } else {
	        templateBody.append (nextPart.content);
	        if (!nextPart.content.trim ().endsWith (";")) {
	            templateBody.append (";");
	        }
	    }
	}
	// templateBody.append ("\r\nreturn null;\r\n");

	return templateBody.toString ();

    }

    public void remove () {
	prototype.removeTemplate (name);
	if (file != null)
	    prototype.removeUpdatable (file.getName());
    }



    class Part {

	String content;
	Template parent;
	boolean isPart;
	boolean isStatic;


	public Part (Template parent, String content, boolean isStatic) {
	    isPart = false;
	    this.parent = parent;
	    this.content = content;
	    this.isStatic = isStatic;
	}

	public String getName () {
	    return isStatic ? null : content;
	}


	public String toString () {
	    return "Template.Part ["+content+","+isStatic+"]";
	}

    }

}





