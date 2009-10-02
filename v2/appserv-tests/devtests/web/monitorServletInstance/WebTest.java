/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2009 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 *
 * Contributor(s):
 *
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
import java.io.*;
import java.net.*;
import java.net.HttpURLConnection;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import com.sun.ejte.ccl.reporter.*;

import org.apache.catalina.util.Base64;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/*
 * Unit test for Issue 9481: v3 runtime tree is not exposing stastistics for Servlets
 *
 */
public class WebTest {

    private static final String TEST_NAME = "monitor-servlet-instance";
    private static final String EXPECTED = "OK";

    private static SimpleReporterAdapter stat
        = new SimpleReporterAdapter("appserv-tests");

    private String adminHost;
    private String adminPort;
    private String adminUser;
    private String adminPassword;
    private String host;
    private String port;
    private String contextRoot;

    public WebTest(String[] args) {
        adminHost = args[0];
        adminPort = args[1];
        adminUser = args[2];
        adminPassword = args[3];
        host = args[4];
        port = args[5];
        contextRoot = args[6];
    }
    
    public static void main(String[] args) {
        stat.addDescription("Unit test for issue 9481");
        WebTest webTest = new WebTest(args);
        webTest.doTest();
	    stat.printSummary();
    }

    public void doTest() {
        try {
            long appReqCount1 = getValue("test/requestcount", "RequestCount", "Count");
            System.out.println("app request count: " + appReqCount1);
            long processTime1 = getValue("test/processingtime", "ProcessingTime", "LastSampleTime");
            System.out.println("app process time: " + processTime1);

            String testResult = invokeURL("http://" + host + ":" + port + contextRoot + "/test");
            System.out.println(testResult);
            
            long appReqCount2 = getValue("test/requestcount", "RequestCount", "Count");
            System.out.println("app request count: " + appReqCount2);
            long processTime2 = getValue("test/processingtime", "ProcessingTime", "LastSampleTime");
            System.out.println("app process time: " + processTime2);

            boolean ok1 = (EXPECTED.equals(testResult) &&
                    (appReqCount1 >= 0 && appReqCount2 == (appReqCount1 + 1)) &&
                    (processTime2 > 0));

            long appErrorCount1 = getValue("badrequest/errorcount", "ErrorCount", "Count");
            System.out.println("app error count: " + appErrorCount1);

            invokeURL("http://" + host + ":" + port + contextRoot + "/badrequest");
            
            long appErrorCount2 = getValue("badrequest/errorcount", "ErrorCount", "Count");
            System.out.println("app error count: " + appErrorCount2);

            boolean ok2 = (appErrorCount1 >= 0 && appErrorCount2 == (appErrorCount1 + 1));

            boolean ok = ok1 && ok2;
            stat.addStatus(TEST_NAME, ((ok)? stat.PASS : stat.FAIL));
        } catch(Exception ex) {
            ex.printStackTrace();
            stat.addStatus(TEST_NAME, stat.FAIL);
        }
    }

    private String invokeURL(String urlString) throws Exception {
     
        StringBuilder sb = new StringBuilder();

        URL url = new URL(urlString);
        System.out.println("Connecting to: " + url.toString());
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.addRequestProperty("accept", "application/xml");
        if (adminPassword != null) {
            conn.setRequestProperty("Authorization", "Basic " +
                new String(Base64.encode((adminUser + ":" + adminPassword).getBytes())));
        }
        conn.connect();
        int responseCode = conn.getResponseCode();

        if (responseCode == HttpURLConnection.HTTP_OK) {
            InputStream is = null;
            BufferedReader reader = null;
            try {
                is = conn.getInputStream();
                reader = new BufferedReader(new InputStreamReader(is));
                String line = null;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            } finally {
                if (is != null) {
                    try {
                        is.close();
                    } catch(IOException ex) {
                    }
                }
                if (reader != null) {
                    try {
                        reader.close();
                    } catch(IOException ex) {
                    }
                }
            }
        } else {
            System.out.println("Get response code: " + responseCode);
        }

        return sb.toString();
    }

    private long getValue(String monitorPath, String countName, String attrName) throws Exception {
        String result = invokeURL("http://" + adminHost + ":" + adminPort +
                "/monitoring/domain/server/applications" + 
                contextRoot + "-web/server/" + monitorPath);
        
        return parseValue(result, countName, attrName);
    }

    private long parseValue(String resultStr, String countName, String attrName) throws Exception {
        long count = -1L;
        System.out.println("parseValue: " + resultStr);
        if (resultStr != null) {
            DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = db.parse(new InputSource(new ByteArrayInputStream(resultStr.getBytes())));
            NodeList nlist = doc.getDocumentElement().getElementsByTagName(countName);
            if (nlist != null && nlist.getLength() > 0) {
                Element element = (Element) nlist.item(0);
                String countStr = element.getAttribute(attrName);
                if (countStr != null) {
                    count = Long.parseLong(countStr);
                }
            }
        }
        return count;
    }
}
