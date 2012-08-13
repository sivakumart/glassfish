/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package org.glassfish.admin.rest.readers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.validation.ConstraintViolation;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.Provider;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.glassfish.admin.rest.composite.CompositeUtil;
import org.glassfish.admin.rest.composite.RestModel;

/**
 *
 * @author jdlee
 */
@Provider
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RestModelReader<T extends RestModel> implements MessageBodyReader<T> {
    @Override
    public T readFrom(Class<T> type, Type type1, Annotation[] antns, MediaType mt, MultivaluedMap<String, String> mm, InputStream entityStream) throws WebApplicationException {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(entityStream));
            StringBuilder sb = new StringBuilder();
            String line = in.readLine();
            while (line != null) {
                sb.append(line);
                line = in.readLine();
            }

            JSONObject o = new JSONObject(sb.toString());
            T model = CompositeUtil.instance().unmarshallClass(type, o);
            Set<ConstraintViolation<T>> cv = CompositeUtil.instance().validateRestModel(model);
            if (!cv.isEmpty()) {
                throw new WebApplicationException(Response.status(Status.BAD_REQUEST)
                        .entity(CompositeUtil.instance().getValidationFailureMessages(cv, model)).build());
            }
            return (T)model;
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
    }

    @Override
    public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        String submittedType = mediaType.toString();
        int index = submittedType.indexOf(";");
        if (index > -1) {
            submittedType = submittedType.substring(0, index);
        }
        return submittedType.equals(MediaType.APPLICATION_JSON) && RestModel.class.isAssignableFrom(type);
    }

    private void setValue (Object model, String key, Object value) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        if (value == null) {
            return;
        }

        String setterName = "set" + key.substring(0,1).toUpperCase() + key.substring(1);
        System.out.println("Setter = " + setterName);
        Method method = null;
        try {
            method = model.getClass().getMethod(setterName, value.getClass());
        } catch (Exception ex) {
        }
        if (method == null) {
            Method[] methods = model.getClass().getMethods();
            for (Method m : methods) {
                if (m.getName().equals(setterName)) {
                    if (m.getParameterTypes()[0].isAssignableFrom(value.getClass())) {
                        method = m;
                        break;
                    }
                }
            }
        }
        if (method != null) {
            method.invoke(model, value);
        }
    }

    private Object getRealObject(Object o) throws JSONException {
        if (o.equals(JSONObject.NULL)) {
            return null;
        } else if (JSONArray.class.isAssignableFrom(o.getClass())) {
            JSONArray array = (JSONArray) o;
            List list = new ArrayList();
            for (int i = 0; i < array.length(); i++) {
                list.add(getRealObject(array.get(i)));
            }
            return list;
        }

        return o;
    }
}