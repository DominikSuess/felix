/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.felix.gogo.felixcommands;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.command.Flag;
import org.osgi.service.packageadmin.PackageAdmin;
import org.osgi.service.startlevel.StartLevel;

public class Basic
{
    private final BundleContext m_bc;
    private final List<ServiceReference> m_usedRefs = new ArrayList(0);

    public Basic(BundleContext bc)
    {
        m_bc = bc;
    }

    public void bundlelevel(Long id)
    {
        // Get start level service.
        ServiceReference ref = m_bc.getServiceReference(StartLevel.class.getName());
        StartLevel sl = getService(StartLevel.class, ref);
        if (sl == null)
        {
            System.out.println("Start Level service is unavailable.");
        }
        // Get the bundle start level.
        else
        {
            Bundle bundle = getBundle(m_bc, id);
            if (bundle != null)
            {
                System.out.println(bundle + " is level " + sl.getBundleStartLevel(bundle));
            }
        }

        ungetServices();
    }

    public void bundlelevel(
        @Flag(name="-s") boolean set,
        @Flag(name="-i") boolean initial,
        int level,
        Long[] ids)
    {
        // Get start level service.
        ServiceReference ref = m_bc.getServiceReference(StartLevel.class.getName());
        StartLevel sl = getService(StartLevel.class, ref);
        if (sl == null)
        {
            System.out.println("Start Level service is unavailable.");
        }
        else if (set && initial)
        {
            System.out.println("Cannot specify '-s' and '-i' at the same time.");
        }
        else if (!set && !initial)
        {
            System.out.println("Must specify either '-s' or '-i'.");
        }
        else if (level <= 0)
        {
            System.out.println("Specified start level must be greater than zero.");
        }
        // Set the initial bundle start level.
        else if (initial)
        {
            if ((ids != null) && (ids.length == 0))
            {
                sl.setInitialBundleStartLevel(level);
            }
            else
            {
                System.out.println(
                    "Cannot specify bundles when setting initial start level.");
            }
        }
        // Set the bundle start level.
        else if (set)
        {
            List<Bundle> bundles = getBundles(m_bc, ids);
            if (bundles != null)
            {
                for (Bundle bundle: bundles)
                {
                    sl.setBundleStartLevel(bundle, level);
                }
            }
            else
            {
                System.out.println("Must specify target bundles.");
            }
        }

        ungetServices();
    }

    public void frameworklevel()
    {
        // Get start level service.
        ServiceReference ref = m_bc.getServiceReference(StartLevel.class.getName());
        StartLevel sl = getService(StartLevel.class, ref);
        if (sl == null)
        {
            System.out.println("Start Level service is unavailable.");
        }
        System.out.println("Level is " + sl.getStartLevel());
        ungetServices();
    }

    public void frameworklevel(int level)
    {
        // Get start level service.
        ServiceReference ref = m_bc.getServiceReference(StartLevel.class.getName());
        StartLevel sl = getService(StartLevel.class, ref);
        if (sl == null)
        {
            System.out.println("Start Level service is unavailable.");
        }
        sl.setStartLevel(level);
        ungetServices();
    }

    public void headers(Long[] ids)
    {
        List<Bundle> bundles = getBundles(m_bc, ids);
        bundles = (bundles == null) ? Arrays.asList(m_bc.getBundles()) : bundles;
        for (Bundle bundle : bundles)
        {
            String title = Util.getBundleName(bundle);
            System.out.println("\n" + title);
            System.out.println(Util.getUnderlineString(title));
            Dictionary dict = bundle.getHeaders();
            Enumeration keys = dict.keys();
            while (keys.hasMoreElements())
            {
                Object k = (String) keys.nextElement();
                Object v = dict.get(k);
                System.out.println(k + " = " + Util.getValueString(v));
            }
        }
    }

    public void help()
    {
        ServiceReference[] refs = null;
        try
        {
            refs = m_bc.getAllServiceReferences(null, "(osgi.command.scope=*)");
        }
        catch (InvalidSyntaxException ex)
        {
            // This should never happen.
        }

        for (ServiceReference ref : refs)
        {
            Object svc = m_bc.getService(ref);
            if (svc != null)
            {
                String[] funcs = (String[]) ref.getProperty("osgi.command.function");

                Map<String, List<Method>> commands = new HashMap();
                for (String func : funcs)
                {
                    commands.put(func, new ArrayList());
                }

                if (!commands.isEmpty())
                {
                    Method[] methods = svc.getClass().getMethods();
                    for (Method method : methods)
                    {
                        List<Method> commandMethods = commands.get(method.getName());
                        if (commandMethods != null)
                        {
                            commandMethods.add(method);
                        }
                    }

                    for (Entry<String, List<Method>> entry : commands.entrySet())
                    {
                        System.out.println(entry.getKey());
                        for (Method m : entry.getValue())
                        {
                            System.out.println("--> " + m);
                        }
                    }
                }
            }
        }
    }

    public void install(String[] urls)
    {
        StringBuffer sb = new StringBuffer();

        for (String url : urls)
        {
            String location = url.trim();
            Bundle bundle = null;
            try
            {
                bundle = m_bc.installBundle(location, null);
            }
            catch (IllegalStateException ex)
            {
                System.err.println(ex.toString());
            }
            catch (BundleException ex)
            {
                if (ex.getNestedException() != null)
                {
                    System.err.println(ex.getNestedException().toString());
                }
                else
                {
                    System.err.println(ex.toString());
                }
            }
            catch (Exception ex)
            {
                System.err.println(ex.toString());
            }
            if (bundle != null)
            {
                if (sb.length() > 0)
                {
                    sb.append(", ");
                }
                sb.append(bundle.getBundleId());
            }
        }
        if (sb.toString().indexOf(',') > 0)
        {
            System.out.println("Bundle IDs: " + sb.toString());
        }
        else if (sb.length() > 0)
        {
            System.out.println("Bundle ID: " + sb.toString());
        }
    }

    public void lb(
        @Flag(name="-l") boolean showLoc,
        @Flag(name="-s") boolean showSymbolic,
        @Flag(name="-u") boolean showUpdate)
    {
        // Get start level service.
        ServiceReference ref = m_bc.getServiceReference(StartLevel.class.getName());
        StartLevel sl = getService(StartLevel.class, ref);
        if (sl == null)
        {
            System.out.println("Start Level service is unavailable.");
        }

        Bundle[] bundles = m_bc.getBundles();
        if (bundles != null)
        {
            printBundleList(bundles, sl, System.out, showLoc, showSymbolic, showUpdate);
        }
        else
        {
            System.out.println("There are no installed bundles.");
        }

        ungetServices();
    }

    public void refresh()
    {
        refresh((List) null);
    }

    public void refresh(Long[] ids)
    {
        List<Bundle> bundles = getBundles(m_bc, ids);
        if ((bundles != null) && !bundles.isEmpty())
        {
            refresh(bundles);
        }
    }

    public void refresh(List<Bundle> bundles)
    {
        // Get package admin service.
        ServiceReference ref = m_bc.getServiceReference(PackageAdmin.class.getName());
        PackageAdmin pa = getService(PackageAdmin.class, ref);
        if (pa == null)
        {
            System.out.println("Package Admin service is unavailable.");
        }
        pa.refreshPackages((bundles == null)
            ? null
            : (Bundle[]) bundles.toArray(new Bundle[bundles.size()]));

        ungetServices();
    }

    public void resolve()
    {
        resolve((List) null);
    }

    public void resolve(Long[] ids)
    {
        List<Bundle> bundles = getBundles(m_bc, ids);
        if ((bundles != null) && !bundles.isEmpty())
        {
            resolve(bundles);
        }
    }

    public void resolve(List<Bundle> bundles)
    {
        // Get package admin service.
        ServiceReference ref = m_bc.getServiceReference(PackageAdmin.class.getName());
        PackageAdmin pa = getService(PackageAdmin.class, ref);
        if (pa == null)
        {
            System.out.println("Package Admin service is unavailable.");
        }
        pa.resolveBundles((bundles == null)
            ? null
            : (Bundle[]) bundles.toArray(new Bundle[bundles.size()]));

        ungetServices();
    }

    public void start(
        @Flag(name="-t") boolean trans,
        @Flag(name="-p") boolean policy,
        String[] ss)
    {
        int options = 0;

        // Check for "transient" switch.
        if (trans)
        {
            options |= Bundle.START_TRANSIENT;
        }

        // Check for "start policy" switch.
        if (policy)
        {
            options |= Bundle.START_ACTIVATION_POLICY;
        }

        // There should be at least one bundle id.
        if ((ss != null) && (ss.length >= 1))
        {
            for (String s : ss)
            {
                String id = s.trim();

                try
                {
                    Bundle bundle = null;

                    // The id may be a number or a URL, so check.
                    if (Character.isDigit(id.charAt(0)))
                    {
                        long l = Long.parseLong(id);
                        bundle = m_bc.getBundle(l);
                    }
                    else
                    {
                        bundle = m_bc.installBundle(id);
                    }

                    if (bundle != null)
                    {
                        bundle.start(options);
                    }
                    else
                    {
                        System.err.println("Bundle ID " + id + " is invalid.");
                    }
                }
                catch (NumberFormatException ex)
                {
                    System.err.println("Unable to parse id '" + id + "'.");
                }
                catch (BundleException ex)
                {
                    if (ex.getNestedException() != null)
                    {
                        ex.printStackTrace();
                        System.err.println(ex.getNestedException().toString());
                    }
                    else
                    {
                        System.err.println(ex.toString());
                    }
                }
                catch (Exception ex)
                {
                    System.err.println(ex.toString());
                }
            }
        }
        else
        {
            System.err.println("Incorrect number of arguments");
        }
    }

    public void stop(Long[] ids) throws BundleException
    {
        List<Bundle> bundles = getBundles(m_bc, ids);

        if (bundles == null)
        {
            System.out.println("Please specify the bundles to start.");
        }
        else
        {
            for (Bundle bundle : bundles)
            {
                bundle.stop();
            }
        }
    }

    public void uninstall(Long[] ids) throws BundleException
    {
        List<Bundle> bundles = getBundles(m_bc, ids);

        if (bundles == null)
        {
            System.out.println("Please specify the bundles to uninstall.");
        }
        else
        {
            for (Bundle bundle : bundles)
            {
                bundle.uninstall();
            }
        }
    }

    public void update(Long id)
    {
        try
        {
            // Get the bundle.
            Bundle bundle = getBundle(m_bc, id);
            if (bundle != null)
            {
                bundle.update();
            }
        }
        catch (BundleException ex)
        {
            if (ex.getNestedException() != null)
            {
                System.err.println(ex.getNestedException().toString());
            }
            else
            {
                System.err.println(ex.toString());
            }
        }
        catch (Exception ex)
        {
            System.err.println(ex.toString());
        }
    }

    public void update(Long id, String location)
    {
        if (location != null)
        {
            try
            {
                // Get the bundle.
                Bundle bundle = getBundle(m_bc, id);
                if (bundle != null)
                {
                    InputStream is = new URL(location).openStream();
                    bundle.update(is);
                }
                else
                {
                    System.err.println("Bundle ID " + id + " is invalid.");
                }
            }
            catch (MalformedURLException ex)
            {
                System.err.println("Unable to parse URL.");
            }
            catch (IOException ex)
            {
                System.err.println("Unable to open input stream: " + ex);
            }
            catch (BundleException ex)
            {
                if (ex.getNestedException() != null)
                {
                    System.err.println(ex.getNestedException().toString());
                }
                else
                {
                    System.err.println(ex.toString());
                }
            }
            catch (Exception ex)
            {
                System.err.println(ex.toString());
            }
        }
        else
        {
            System.err.println("Must specify a location.");
        }
    }

    private static void printBundleList(
        Bundle[] bundles, StartLevel startLevel, PrintStream out, boolean showLoc,
        boolean showSymbolic, boolean showUpdate)
    {
        // Display active start level.
        if (startLevel != null)
        {
            out.println("START LEVEL " + startLevel.getStartLevel());
        }

        // Print column headers.
        String msg = " Name";
        if (showLoc)
        {
           msg = " Location";
        }
        else if (showSymbolic)
        {
           msg = " Symbolic name";
        }
        else if (showUpdate)
        {
           msg = " Update location";
        }
        String level = (startLevel == null) ? "" : "  Level ";
        out.println("   ID " + "  State       " + level + msg);
        for (int i = 0; i < bundles.length; i++)
        {
            // Get the bundle name or location.
            String name = (String)
                bundles[i].getHeaders().get(Constants.BUNDLE_NAME);
            // If there is no name, then default to symbolic name.
            name = (name == null) ? bundles[i].getSymbolicName() : name;
            // If there is no symbolic name, resort to location.
            name = (name == null) ? bundles[i].getLocation() : name;

            // Overwrite the default value is the user specifically
            // requested to display one or the other.
            if (showLoc)
            {
                name = bundles[i].getLocation();
            }
            else if (showSymbolic)
            {
                name = bundles[i].getSymbolicName();
                name = (name == null)
                    ? "<no symbolic name>" : name;
            }
            else if (showUpdate)
            {
                name = (String)
                    bundles[i].getHeaders().get(Constants.BUNDLE_UPDATELOCATION);
                name = (name == null)
                    ? bundles[i].getLocation() : name;
            }
            // Show bundle version if not showing location.
            String version = (String)
                bundles[i].getHeaders().get(Constants.BUNDLE_VERSION);
            name = (!showLoc && !showUpdate && (version != null))
                ? name + " (" + version + ")" : name;
            long l = bundles[i].getBundleId();
            String id = String.valueOf(l);
            if (startLevel == null)
            {
                level = "1";
            }
            else
            {
                level = String.valueOf(startLevel.getBundleStartLevel(bundles[i]));
            }
            while (level.length() < 5)
            {
                level = " " + level;
            }
            while (id.length() < 4)
            {
                id = " " + id;
            }
            out.println("[" + id + "] ["
                + getStateString(bundles[i])
                + "] [" + level + "] " + name);
        }
    }

    private <T> T getService(Class<T> clazz, ServiceReference ref)
    {
        if (ref == null)
        {
            return null;
        }
        T t = (T) m_bc.getService(ref);
        if (t != null)
        {
            m_usedRefs.add(ref);
        }
        return t;
    }

    private void ungetServices()
    {
        while (m_usedRefs.size() > 0)
        {
            m_bc.ungetService(m_usedRefs.remove(0));
        }
    }

    private static String getStateString(Bundle bundle)
    {
        int state = bundle.getState();
        if (state == Bundle.ACTIVE)
        {
            return "Active     ";
        }
        else if (state == Bundle.INSTALLED)
        {
            return "Installed  ";
        }
        else if (state == Bundle.RESOLVED)
        {
            return "Resolved   ";
        }
        else if (state == Bundle.STARTING)
        {
            return "Starting   ";
        }
        else if (state == Bundle.STOPPING)
        {
            return "Stopping   ";
        }
        else
        {
            return "Unknown    ";
        }
    }

    private static Bundle getBundle(BundleContext bc, Long id)
    {
        Bundle bundle = bc.getBundle(id);
        if (bundle == null)
        {
            System.err.println("Bundle ID " + id + " is invalid");
        }
        return bundle;
    }

    private static List<Bundle> getBundles(BundleContext bc, Long[] ids)
    {
        List<Bundle> bundles = new ArrayList<Bundle>();
        if ((ids != null) && (ids.length > 0))
        {
            for (long id : ids)
            {
                Bundle bundle = getBundle(bc, id);
                if (bundle != null)
                {
                    bundles.add(bundle);
                }
            }
        }
        else
        {
            bundles = null;
        }

        return bundles;
    }
}