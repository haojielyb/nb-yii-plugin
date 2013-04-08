/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2013 Oracle and/or its affiliates. All rights reserved.
 *
 * Oracle and Java are registered trademarks of Oracle and/or its affiliates.
 * Other names may be trademarks of their respective owners.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common
 * Development and Distribution License("CDDL") (collectively, the
 * "License"). You may not use this file except in compliance with the
 * License. You can obtain a copy of the License at
 * http://www.netbeans.org/cddl-gplv2.html
 * or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
 * specific language governing permissions and limitations under the
 * License.  When distributing the software, include this License Header
 * Notice in each file and include the License file at
 * nbbuild/licenses/CDDL-GPL-2-CP.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the GPL Version 2 section of the License file that
 * accompanied this code. If applicable, add the following below the
 * License Header, with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * If you wish your version of this file to be governed by only the CDDL
 * or only the GPL Version 2, indicate your decision by adding
 * "[Contributor] elects to include this software in this distribution
 * under the [CDDL or GPL Version 2] license." If you do not indicate a
 * single choice of license, a recipient has the option to distribute
 * your version of this file under either the CDDL, the GPL Version 2 or
 * to extend the choice of license to its licensees as provided above.
 * However, if you add GPL Version 2 code and therefore, elected the GPL
 * Version 2 license, then the option applies only if the new code is
 * made subject to such option by the copyright holder.
 *
 * Contributor(s):
 *
 * Portions Copyrighted 2013 Sun Microsystems, Inc.
 */
package org.nbphpcouncil.modules.php.yii.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.nbphpcouncil.modules.php.yii.Yii;
import org.nbphpcouncil.modules.php.yii.YiiModule;
import org.nbphpcouncil.modules.php.yii.YiiModuleFactory;
import org.nbphpcouncil.modules.php.yii.YiiPhpFrameworkProvider;
import org.nbphpcouncil.modules.php.yii.preferences.YiiPreferences;
import org.netbeans.modules.csl.spi.ParserResult;
import org.netbeans.modules.parsing.api.ParserManager;
import org.netbeans.modules.parsing.api.ResultIterator;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.modules.parsing.api.UserTask;
import org.netbeans.modules.parsing.spi.ParseException;
import org.netbeans.modules.php.api.editor.PhpClass;
import org.netbeans.modules.php.api.phpmodule.PhpModule;
import org.netbeans.modules.php.api.util.FileUtils;
import org.netbeans.modules.php.api.util.StringUtils;
import org.netbeans.modules.php.editor.parser.api.Utils;
import org.netbeans.modules.php.editor.parser.astnodes.ArrayElement;
import org.netbeans.modules.php.editor.parser.astnodes.Expression;
import org.netbeans.modules.php.editor.parser.astnodes.visitors.DefaultVisitor;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;

/**
 *
 * @author junichi11
 */
public class YiiUtils {

    private static final String YII_INCLUDE_PATH_REGEX = "^\\$yii *=.+'(.+/framework)/yii\\.php';$"; // NOI18N
    private static final String CONTROLLER_SUFFIX = "Controller"; // NOI18N
    private static final String CONTROLLERS_DIRECTORY_NAME = "controllers"; // NOI18N
    private static final String ACTION_METHOD_PREFIX = "action"; // NOI18N
    private static final String VIEW_RELATIVE_PATH_FORMAT = "../..%s%s/views/%s%s/%s.php"; // NOI18N
    private static final String CONTROLLER_RELATIVE_PATH_FORMAT = "../../..%s%s/controllers/%s%s.php"; // NOI18N
    private static final String THEME_PATH = "/../themes/%s"; // NOI18N
    private static final String NBPROJECT = "nbproject"; // NOI18N
    private static final String PROTECTED_PATH = "protected/"; // NOI18N
    private static final String VIEWS_PATH = PROTECTED_PATH + "views"; // NOI18N
    private static final String CONTROLLERS_PATH = PROTECTED_PATH + "controllers"; // NOI18N
    private static final String MODELS_PATH = PROTECTED_PATH + "models"; // NOI18N
    private static final String TESTS_PATH = PROTECTED_PATH + "tests"; // NOI18N
    private static final String CONFIG_PATH = PROTECTED_PATH + "config"; // NOI18N
    private static final String THEMES_PATH = "themes"; // NOI18N
    private static final Logger LOGGER = Logger.getLogger(YiiUtils.class.getName());

    /**
     * Check whether php module is yii
     *
     * @param phpModule
     * @return true if php module is yii, otherwiser false
     */
    public static boolean isYii(PhpModule phpModule) {
        if (phpModule == null) {
            return false;
        }
        return YiiPhpFrameworkProvider.getInstance().isInPhpModule(phpModule);
    }

    private static PhpModule getPhpModule(FileObject fileObject) {
        return PhpModule.forFileObject(fileObject);
    }

    /**
     * Get include path.
     *
     * @param index index.php file
     * @return if exists include path, string. otherwise null.
     */
    public static List<String> getIncludePath(FileObject index) {
        List<String> lines = null;
        List<String> includePath = new ArrayList<String>();
        try {
            lines = index.asLines("UTF-8"); // NOI18N
            Pattern pattern = Pattern.compile(YII_INCLUDE_PATH_REGEX);
            for (String line : lines) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    String path = matcher.group(1);
                    if (path.startsWith("/")) { // NOI18N
                        path = path.replaceFirst("/", ""); // NOI18N
                    }
                    includePath.add(path);
                    break;
                }
            }
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
        return includePath;
    }

    /**
     * Check view file
     *
     * @param fo
     * @return true if file is view file, otherwise false.
     */
    public static boolean isView(FileObject fo) {
        if (fo == null
                || !fo.isData()
                || !FileUtils.isPhpFile(fo)) {
            return false;
        }

        String subpath = getPathFromWebroot(fo);
        if (StringUtils.isEmpty(subpath)) {
            return false;
        }
        if (subpath.contains("/views/")) { // NOI18N
            return true;
        }

        return false;
    }

    /**
     * Get path from webroot directory.
     *
     * @param fo
     * @return
     */
    private static String getPathFromWebroot(FileObject fo) {
        PhpModule phpModule = getPhpModule(fo);
        // return null if use external files #11
        if (phpModule == null) {
            return null;
        }
        String path = fo.getPath();
        YiiModule yiiModule = YiiModuleFactory.create(phpModule);
        FileObject webroot = yiiModule.getWebroot();
        String webrootPath = webroot.getPath();
        return path.replace(webrootPath, ""); // NOI18N
    }

    /**
     * Get view file object
     *
     * @param controller
     * @param method
     * @return view file object
     */
    public static FileObject getView(FileObject controller, PhpClass.Method method) {
        String viewFolderName = getViewFolderName(controller.getName());
        String viewName = getViewName(method);
        String subPath = getSubDirectoryPathForController(controller);
        if (subPath == null) {
            return null;
        }
        String pathToView = getRelativePathToView(controller, subPath, viewFolderName, viewName);
        if (pathToView == null) {
            return null;
        }

        // get view file
        FileObject view = controller.getFileObject(pathToView);

        // use "Create a new view file automatically" option
        PhpModule phpModule = PhpModule.forFileObject(controller);
        if (view == null && YiiPreferences.useAutoCreateView(phpModule)) {
            view = createViewFileAuto(controller, pathToView);
        }
        return view;
    }

    /**
     * Get relative path to view file from controller.
     *
     * @param controller
     * @param actionId action name(view file name). e.g actionIndex -> index
     * @return view file object
     */
    public static String getRelativePathToView(FileObject controller, String actionId) {
        String controllerId = getViewFolderName(controller.getName());
        String subPath = getSubDirectoryPathForController(controller);
        if (subPath == null) {
            return null;
        }
        return getRelativePathToView(controller, subPath, controllerId, actionId);
    }

    /**
     * Get sub directory path for controller file. If file doesn't exist within
     * protected/controllers, return null. Otherwise return empty string "" or
     * path. For example : protected/controllers/subdir/SiteController.php ->
     * subdir/
     *
     * @param controller
     * @return sub directory path from controllers dir to file.
     */
    private static String getSubDirectoryPathForController(FileObject controller) {
        String filePath = controller.getPath();
        if (!filePath.contains("/controllers/")) { // NOI18N
            return null;
        }
        String subPath = filePath.replaceAll(".+/controllers/", ""); // NOI18N
        return subPath.replace(controller.getNameExt(), ""); // NOI18N
    }

    /**
     * Get relative path to view file from controller.
     *
     * @param controller
     * @param controllerId
     * @param actionId
     * @return
     */
    private static String getRelativePathToView(FileObject controller, String subPath, String controllerId, String actionId) {
        if (controller == null) {
            return null;
        }
        PhpModule phpModule = PhpModule.forFileObject(controller);
        YiiModule yiiModule = YiiModuleFactory.create(phpModule);
        FileObject sourceDirectory = yiiModule.getWebroot();

        // get main.php
        FileObject main = null;
        if (sourceDirectory != null) {
            main = sourceDirectory.getFileObject(CONFIG_PATH + "/main.php"); // NOI18N
        }
        if (main == null) {
            LOGGER.log(Level.INFO, "Not found main.php");
            return null;
        }

        // get theme
        String themeName = getThemeName(main);
        String themePath = ""; // NOI18N
        if (!themeName.isEmpty()) {
            themePath = String.format(THEME_PATH, themeName);
        }

        // add depth for sub path
        String subpathDepth = toSubpathDepth(subPath);

        // create relative path from controller to view file
        return String.format(VIEW_RELATIVE_PATH_FORMAT, subpathDepth, themePath, subPath, controllerId, actionId);
    }

    /**
     * Get theme name from main.php file.
     *
     * @param main
     * @return theme name if find the theme, otherwise empty string.
     */
    public static String getThemeName(FileObject main) {
        final Set<String> themes = new HashSet<String>();
        try {
            ParserManager.parse(Collections.singleton(Source.create(main)), new UserTask() {
                @Override
                public void run(ResultIterator resultIterator) throws Exception {
                    if (resultIterator == null) {
                        return;
                    }
                    ParserResult parserResult = (ParserResult) resultIterator.getParserResult();
                    final MainVisitor mainVisitor = new MainVisitor();
                    mainVisitor.scan(Utils.getRoot(parserResult));
                    themes.addAll(mainVisitor.getThemeName());
                }
            });
        } catch (ParseException ex) {
            Exceptions.printStackTrace(ex);
        }
        String themeName = ""; // NOI18N
        for (String theme : themes) {
            themeName = theme;
            break;
        }
        return themeName;
    }

    /**
     * For auto create option.
     *
     * @param controller
     * @param pathToView
     * @return
     */
    public static FileObject createViewFileAuto(FileObject controller, String pathToView) {
        String viewPath = FileUtil.normalizePath(controller.getParent().getPath() + pathToView);
        File file = new File(viewPath);
        FileObject view = null;
        try {
            // create sub directories
            File parentFile = file.getParentFile();
            if (!parentFile.exists()) {
                parentFile.mkdirs();
            }
            // create file
            if (file.createNewFile()) {
                view = FileUtil.toFileObject(file);
            }
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Can't create view file : {0}", viewPath);
        }
        return view;
    }

    /**
     * Check controller file
     *
     * @param fo
     * @return true if file is controller file, otherwise false.
     */
    public static boolean isController(FileObject fo) {
        String subPath = getSubDirectoryPathForController(fo);
        if (subPath == null) {
            return false;
        }
        return fo.isData() && FileUtils.isPhpFile(fo) && fo.getName().endsWith(CONTROLLER_SUFFIX);
    }

    /**
     * Get controller file object
     *
     * @param view
     * @return controller file object
     */
    public static FileObject getController(FileObject view) {
        if (!isView(view)) {
            return null;
        }

        // get relative path
        String subpathFromWebroot = getPathFromWebroot(view);

        // themes
        String themeDepth = ""; // NOI18N
        if (subpathFromWebroot.contains("/themes/")) { // NOI18N
            themeDepth = "/../../protected"; // NOI18N
        }

        // views
        String subpath = ""; // NOI18N
        String controllerId = ""; // NOI18N
        if (subpathFromWebroot.contains("/views/")) { // NOI18N
            subpath = subpathFromWebroot.replaceAll(".+/views/", ""); // NOI18N
            subpath = subpath.replace("/" + view.getNameExt(), ""); // NOI18N
            int lastSlash = subpath.lastIndexOf("/"); // NOI18N
            if (lastSlash == -1) {
                controllerId = subpath;
                subpath = ""; // NOI18N
            } else {
                controllerId = subpath.substring(lastSlash + 1);
                subpath = subpath.substring(0, lastSlash + 1);
            }
        }

        // add depth for sub path
        String subpathDepth = toSubpathDepth(subpath);
        String controllerName = getControllerFileName(controllerId);
        String format = String.format(CONTROLLER_RELATIVE_PATH_FORMAT, subpathDepth, themeDepth, subpath, controllerName);
        return view.getFileObject(format);
    }

    /**
     * Get subpath depth.
     *
     * @param path
     * @return
     */
    private static String toSubpathDepth(String path) {
        StringBuilder sb = new StringBuilder();
        int startIndex = 0;
        while (startIndex != -1) {
            startIndex++;
            startIndex = path.indexOf("/", startIndex); // NOI18N
            if (startIndex > 0) {
                sb.append("/.."); // NOI18N
            }
        }
        return sb.toString();
    }

    /**
     * Get controller file name. e.g. SiteController,DemoController
     *
     * @param viewFolderName each views directory name
     * @return controller file name. ControllerName + Controller
     */
    public static String getControllerFileName(String viewFolderName) {
        viewFolderName = toFirstUpperCase(viewFolderName);
        if (viewFolderName == null) {
            return null;
        }
        return viewFolderName + CONTROLLER_SUFFIX;
    }

    /**
     * Check controller class name
     *
     * @param controller
     * @return true if controller class, otherwise false
     */
    public static boolean isControllerName(String controller) {
        if (controller == null) {
            return false;
        }
        return controller.endsWith(CONTROLLER_SUFFIX);
    }

    /**
     * Get controller action method name
     *
     * @param view file name
     * @return action method name
     */
    public static String getActionMethodName(String view) {
        if (view == null || view.isEmpty()) {
            return null;
        }
        return ACTION_METHOD_PREFIX + toFirstUpperCase(view);
    }

    /**
     * Get view folder name
     *
     * @param controllerName
     * @return
     */
    public static String getViewFolderName(String controllerName) {
        if (!isControllerName(controllerName)) {
            return null;
        }
        String name = controllerName.replace(CONTROLLER_SUFFIX, ""); // NOI18N
        name = name.toLowerCase();
        return name;
    }

    /**
     * Get view name
     *
     * @param method
     * @return
     */
    public static String getViewName(PhpClass.Method method) {
        String name = method.getName();
        if (!isActionMethodName(name)) {
            return null;
        }
        name = name.replace(ACTION_METHOD_PREFIX, ""); // NOI18N
        return name.toLowerCase();
    }

    /**
     * Check action method name
     *
     * @param name
     * @return
     */
    public static boolean isActionMethodName(String name) {
        if (name == null || !name.startsWith(ACTION_METHOD_PREFIX) || name.isEmpty()) {
            return false;
        }
        name = name.replace(ACTION_METHOD_PREFIX, ""); // NOI18N
        String first = name.substring(0, 1);
        if (!first.equals(first.toUpperCase())) {
            return false;
        }
        return true;
    }

    /**
     * Converts the first of characters to upper case.
     *
     * Examples: "apple" -> "Apple", "Banana" -> "Banana", "ORANGE" -> "Orange"
     *
     * @param string
     * @return the converted string
     */
    public static String toFirstUpperCase(String string) {
        if (string == null || string.isEmpty()) {
            return null;
        }
        string = string.toLowerCase();
        return string.substring(0, 1).toUpperCase() + string.substring(1);
    }

    /**
     * Create code completion file
     *
     * @param phpModule
     * @throws IOException
     */
    public static void createCodeCompletionFile(PhpModule phpModule) throws IOException {
        if (!isYii(phpModule)) {
            return;
        }
        FileObject codeCompletion = FileUtil.getConfigFile(Yii.YII_CODE_COMPLETION_CONFIG_PATH);
        FileObject nbproject = getNbproject(phpModule);
        if (nbproject != null && codeCompletion != null) {
            codeCompletion.copy(nbproject, codeCompletion.getName(), codeCompletion.getExt());
        }
    }

    /**
     * Get nbproject directory
     *
     * @param phpModule
     * @return
     */
    public static FileObject getNbproject(PhpModule phpModule) {
        FileObject projectDirectory = phpModule.getProjectDirectory();
        FileObject nbproject = null;
        if (projectDirectory != null) {
            nbproject = projectDirectory.getFileObject(NBPROJECT);
        }
        return nbproject;
    }

    /**
     * Get views directory (protected/views)
     *
     * @param phpModule
     * @return
     */
    public static FileObject getViewsDirectory(PhpModule phpModule) {
        return getDirectory(phpModule, VIEWS_PATH);
    }

    /**
     * Get controllers directory (protected/controllers)
     *
     * @param phpModule
     * @return
     */
    public static FileObject getControllersDirectory(PhpModule phpModule) {
        return getDirectory(phpModule, CONTROLLERS_PATH);
    }

    /**
     * Get models directory (protected/models)
     *
     * @param phpModule
     * @return
     */
    public static FileObject getModelsDirectory(PhpModule phpModule) {
        return getDirectory(phpModule, MODELS_PATH);
    }

    /**
     * Get tests directory (protected/tests)
     *
     * @param phpModule
     * @return
     */
    public static FileObject getTestsDirectory(PhpModule phpModule) {
        return getDirectory(phpModule, TESTS_PATH);
    }

    /**
     * Get themes directory (protected/themes).
     *
     * @param phpModule
     * @return
     */
    public static FileObject getThemesDirectory(PhpModule phpModule) {
        return getDirectory(phpModule, THEMES_PATH);
    }

    /**
     * Get directory
     *
     * @param phpModule
     * @param path relative path from source directory
     * @return
     */
    public static FileObject getDirectory(PhpModule phpModule, String path) {
        YiiModule yiiModule = YiiModuleFactory.create(phpModule);
        FileObject sourceDirectory = yiiModule.getWebroot();
        if (sourceDirectory == null) {
            return null;
        }
        return sourceDirectory.getFileObject(path);
    }

    private static class MainVisitor extends DefaultVisitor {

        private static final String THEME = "theme"; // NOI18N
        private Set<String> themeName = new HashSet<String>();

        @Override
        public void visit(ArrayElement node) {
            super.visit(node);
            Expression key = node.getKey();
            String keyName = YiiCodeUtils.getStringValue(key);
            if (keyName.equals(THEME)) {
                String value = YiiCodeUtils.getStringValue(node.getValue());
                if (!value.isEmpty()) {
                    themeName.add(value);
                }
            }
        }

        public synchronized Set<String> getThemeName() {
            return themeName;
        }
    }

    /**
     * Check whether file is within modules directory.
     *
     * @param fileObject current file
     * @return true if file exists within modules directory, otherwise false.
     */
    public static boolean isInModules(FileObject fileObject) {
        String pathFromWebroot = getPathFromWebroot(fileObject);
        if (pathFromWebroot != null) {
            return pathFromWebroot.contains("/modules/"); // NOI18N
        }
        return false;
    }

    /**
     * Get module name.
     *
     * @param fileObject
     * @return module name in current file. if file doesn't exist in modules,
     * null.
     */
    public static String getModuleName(FileObject fileObject) {
        if (fileObject == null || !isInModules(fileObject)) {
            return null;
        }
        String path = getPathFromWebroot(fileObject);
        if (path == null) {
            return null;
        }

        path = path.replaceAll(".+/modules/", path); // NOI18N
        return path.substring(0, path.indexOf("/")); // NOI18N
    }

    /**
     * Get current module directory.
     *
     * @param fileObject current file
     * @return current module directory.
     */
    public static FileObject getCurrentModuleDirectory(FileObject fileObject) {
        if (fileObject == null || !isInModules(fileObject)) {
            return null;
        }
        String path = getPathFromWebroot(fileObject);
        if (path == null) {
            return null;
        }
        String modules = "/modules/"; // NOI18N
        int modulesIndex = path.lastIndexOf(modules);

        // contains module name
        int moduleIndex = path.indexOf("/", modulesIndex + modules.length()); // NOI18N
        String modulePath = path.substring(0, moduleIndex);
        YiiModule yiiModule = YiiModuleFactory.create(PhpModule.forFileObject(fileObject));
        FileObject webroot = yiiModule.getWebroot();
        if (webroot == null) {
            return null;
        }
        return webroot.getFileObject(modulePath);
    }
}
