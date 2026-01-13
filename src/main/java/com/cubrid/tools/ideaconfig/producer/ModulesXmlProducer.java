package com.cubrid.tools.ideaconfig.producer;

import com.cubrid.tools.ideaconfig.model.Bundle;
import com.cubrid.tools.ideaconfig.util.XmlHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Produces the .idea/modules.xml file that lists all project modules.
 */
public class ModulesXmlProducer {

    private static final Logger log = LoggerFactory.getLogger(ModulesXmlProducer.class);

    private final Path ideaConfigDir;
    private final Path modulesDir;

    public ModulesXmlProducer(Path ideaConfigDir, Path modulesDir) {
        this.ideaConfigDir = ideaConfigDir;
        this.modulesDir = modulesDir;
    }

    /**
     * Generate the modules.xml file.
     *
     * @param bundles the bundles (modules) to include
     * @throws IOException if file writing fails
     */
    public void generate(List<Bundle> bundles) throws IOException {
        log.info("Generating modules.xml with {} modules", bundles.size());

        Files.createDirectories(ideaConfigDir);

        Path modulesXmlFile = ideaConfigDir.resolve("modules.xml");

        Document doc = XmlHelper.createDocument();

        // Root project element
        Element project = doc.createElement("project");
        project.setAttribute("version", "4");
        doc.appendChild(project);

        // Component: ProjectModuleManager
        Element component = doc.createElement("component");
        component.setAttribute("name", "ProjectModuleManager");
        project.appendChild(component);

        // Modules element
        Element modules = doc.createElement("modules");
        component.appendChild(modules);

        // Add each bundle as a module
        for (Bundle bundle : bundles) {
            Element module = doc.createElement("module");

            String moduleName = bundle.getSymbolicName();
            String imlPath = getImlPath(moduleName);

            module.setAttribute("fileurl", "file://$PROJECT_DIR$/" + imlPath);
            module.setAttribute("filepath", "$PROJECT_DIR$/" + imlPath);

            modules.appendChild(module);
        }

        // Write file
        XmlHelper.writeDocument(doc, modulesXmlFile);
        log.info("Written: {}", modulesXmlFile);
    }

    /**
     * Get the relative path to the .iml file from project root.
     */
    private String getImlPath(String moduleName) {
        // Assuming modules are in the modules/ directory relative to project root
        Path imlFile = modulesDir.resolve(moduleName + ".iml");

        // Get relative path from ideaConfigDir parent (which is the workspace dir)
        Path workspaceDir = ideaConfigDir.getParent();
        if (workspaceDir != null) {
            try {
                Path relativePath = workspaceDir.relativize(imlFile);
                return relativePath.toString().replace('\\', '/');
            } catch (IllegalArgumentException e) {
                // Fall through
            }
        }

        return "modules/" + moduleName + ".iml";
    }
}
