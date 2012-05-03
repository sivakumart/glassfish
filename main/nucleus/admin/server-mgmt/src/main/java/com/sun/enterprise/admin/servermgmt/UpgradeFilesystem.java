/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.enterprise.admin.servermgmt;

import com.sun.enterprise.util.SystemPropertyConstants;

import org.jvnet.hk2.annotations.Service;
import org.glassfish.hk2.api.PostConstruct;
import org.glassfish.api.admin.config.ConfigurationUpgrade;

import java.io.File;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Startup service to update the filesystem from v2 to the v3 format
 *
 * @author Joe Di Pol
 */
@Service
public class UpgradeFilesystem implements ConfigurationUpgrade, PostConstruct {


    @Override
    public void postConstruct() {
        upgradeFilesystem();
    }

    private void upgradeFilesystem() {

        // Rename nodeagents to nodes
        String installDir = System.getProperty(
                SystemPropertyConstants.INSTALL_ROOT_PROPERTY);

        File agentsDir = new File(installDir, "nodeagents");
        File nodesDir = new File(installDir, "nodes");

        // Only do this if nodeagents exists and nodes does not
        if (agentsDir.exists() && ! nodesDir.exists() ) {
            String msg = "Renaming " + agentsDir.getPath() +
                        " to " + nodesDir.getPath();
            Logger.getAnonymousLogger().log(Level.INFO, msg);
            if ( ! agentsDir.renameTo(nodesDir)) {
                msg = "Failed to rename " + agentsDir.getPath() +
                        " to " + nodesDir.getPath();
                Logger.getAnonymousLogger().log(Level.SEVERE, msg);
            }
        }
    }
}
