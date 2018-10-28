/*
	Milyn - Copyright (C) 2006 - 2010

	This library is free software; you can redistribute it and/or
	modify it under the terms of the GNU Lesser General Public
	License (version 2.1) as published by the Free Software
	Foundation.

	This library is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

	See the GNU Lesser General Public License for more details:
	http://www.gnu.org/licenses/lgpl.txt
*/
package org.milyn.javabean.pojogen;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.StringWriter;
import java.io.IOException;
import java.util.List;

import org.milyn.io.NullWriter;
import org.milyn.io.StreamUtils;
import org.milyn.javabean.DataDecoder;
import org.milyn.javabean.DecodeType;
import org.milyn.profile.BasicProfile;
import org.milyn.profile.Profile;

/**
 * @author <a href="mailto:tom.fennelly@jboss.com">tom.fennelly@jboss.com</a>
 */
public class PojoGenTest {

	@Test
    public void test_01() throws IOException {
        JClass aClass = new JClass("com.acme", "AClass");
        JClass bClass = new JClass("com.acme", "BClass");

        aClass.setFluentSetters(true);
        bClass.setFluentSetters(false);

        aClass.addBeanProperty(new JNamedType(new JType(int.class), "primVar"));
        aClass.addBeanProperty(new JNamedType(new JType(Double.class), "doubleVar"));
        aClass.addBeanProperty(new JNamedType(new JType(BBBClass.class), "objVar"));
        aClass.addBeanProperty(new JNamedType(new JType(List.class, BBBClass.class), "genericVar"));

        aClass.getImplementTypes().add(new JType(DataDecoder.class));
        aClass.getImplementTypes().add(new JType(Profile.class));

        aClass.getExtendTypes().add(new JType(NullWriter.class));
        aClass.getExtendTypes().add(new JType(BasicProfile.class));

        aClass.getAnnotationTypes().add(new JType(DecodeType.class));

        // Wire AClass into BClass...
        bClass.addBeanProperty(new JNamedType(new JType(BBBClass.class), "bbbVar"));
        bClass.addBeanProperty(new JNamedType(new JType(aClass.getSkeletonClass()), "aClassVar"));

        StringWriter aWriter = new StringWriter();
        StringWriter bWriter = new StringWriter();

        aClass.writeClass(aWriter);
        String aS = aWriter.toString();
        assertEquals(StreamUtils.trimLines(AClass_Expected), StreamUtils.trimLines(aS));

        bClass.writeClass(bWriter);
        String bS = bWriter.toString();
        assertEquals(StreamUtils.trimLines(BClass_Expected), StreamUtils.trimLines(bS));
    }

	@Test
    public void test_duplicateProperty() {
        JClass aClass = new JClass("com.acme", "AClass");

        aClass.addBeanProperty(new JNamedType(new JType(Double.class), "xVar"));
        try {
            aClass.addBeanProperty(new JNamedType(new JType(Integer.class), "xVar"));
            fail("Exected IllegalArgumentException.");
        } catch(IllegalArgumentException e) {
            assertEquals("Property 'xVar' already defined.", e.getMessage());
        }
    }

    private static String AClass_Expected = "/**\n" +
            " * This class was generated by Smooks EJC (http://www.smooks.org).\n" +
            " */\n" +
            "package com.acme;\n" +
            "\n" +
            "import org.milyn.javabean.DataDecoder;    \n" +
            "import org.milyn.profile.Profile;    \n" +
            "import org.milyn.io.NullWriter;    \n" +
            "import org.milyn.profile.BasicProfile;    \n" +
            "import org.milyn.javabean.DecodeType;     \n" +
            "import org.milyn.javabean.pojogen.BBBClass;    \n" +
            "import java.util.List;    \n" +
            "\n" +
            "@DecodeType" +
            "public class AClass implements DataDecoder, Profile extends NullWriter, BasicProfile {\n" +
            "\n" +
            "    private int primVar;\n" +
            "    private Double doubleVar;\n" +
            "    private BBBClass objVar;\n" +
            "    private List<BBBClass> genericVar;\n" +
            "\n" +
            "    public int getPrimVar() {\n" +
            "        return primVar;\n" +
            "    }\n" +
            "\n" +
            "    public AClass setPrimVar(int primVar) {\n" +
            "        this.primVar = primVar;  return this;\n" +
            "    }\n" +
            "\n" +
            "    public Double getDoubleVar() {\n" +
            "        return doubleVar;\n" +
            "    }\n" +
            "\n" +
            "    public AClass setDoubleVar(Double doubleVar) {\n" +
            "        this.doubleVar = doubleVar;  return this;\n" +
            "    }\n" +
            "\n" +
            "    public BBBClass getObjVar() {\n" +
            "        return objVar;\n" +
            "    }\n" +
            "\n" +
            "    public AClass setObjVar(BBBClass objVar) {\n" +
            "        this.objVar = objVar;  return this;\n" +
            "    }\n" +
            "\n" +
            "    public List<BBBClass> getGenericVar() {\n" +
            "        return genericVar;\n" +
            "    }\n" +
            "\n" +
            "    public AClass setGenericVar(List<BBBClass> genericVar) {\n" +
            "        this.genericVar = genericVar;  return this;\n" +
            "    }\n" +
            "}";

    private static String BClass_Expected = "/**\n" +
            " * This class was generated by Smooks EJC (http://www.smooks.org).\n" +
            " */\n" +
            "package com.acme;\n" +
            "\n" +
            "import org.milyn.javabean.pojogen.BBBClass;    \n" +
            "\n" +
            "public class BClass {\n" +
            "\n" +
            "    private BBBClass bbbVar;\n" +
            "    private AClass aClassVar;\n" +
            "\n" +
            "    public BBBClass getBbbVar() {\n" +
            "        return bbbVar;\n" +
            "    }\n" +
            "\n" +
            "    public void setBbbVar(BBBClass bbbVar) {\n" +
            "        this.bbbVar = bbbVar;\n" +
            "    }\n" +
            "\n" +
            "    public AClass getAClassVar() {\n" +
            "        return aClassVar;\n" +
            "    }\n" +
            "\n" +
            "    public void setAClassVar(AClass aClassVar) {\n" +
            "        this.aClassVar = aClassVar;\n" +
            "    }\n" +
            "}";
}