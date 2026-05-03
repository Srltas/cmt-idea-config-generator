package com.cubrid.tools.ideaconfig.producer;

import com.cubrid.tools.ideaconfig.model.Bundle;
import com.cubrid.tools.ideaconfig.model.TestModule;
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

    public void generate(List<Bundle> bundles, List<TestModule> testModules) throws IOException {
        log.info("Generating modules.xml ({} bundles + {} test modules)",
            bundles.size(), testModules.size());

        Files.createDirectories(ideaConfigDir);
        Path modulesXmlFile = ideaConfigDir.resolve("modules.xml");

        Document doc = XmlHelper.createDocument();
        Element project = doc.createElement("project");
        project.setAttribute("version", "4");
        doc.appendChild(project);

        Element component = doc.createElement("component");
        component.setAttribute("name", "ProjectModuleManager");
        project.appendChild(component);

        Element modules = doc.createElement("modules");
        component.appendChild(modules);

        for (Bundle bundle : bundles) {
            appendModule(doc, modules, bundle.getSymbolicName());
        }
        for (TestModule testModule : testModules) {
            appendModule(doc, modules, testModule.getName());
        }

        XmlHelper.writeDocument(doc, modulesXmlFile);
        log.info("Written: {}", modulesXmlFile);
    }

    private void appendModule(Document doc, Element modules, String moduleName) {
        Element module = doc.createElement("module");
        String imlPath = getImlPath(moduleName);
        module.setAttribute("fileurl", "file://$PROJECT_DIR$/" + imlPath);
        module.setAttribute("filepath", "$PROJECT_DIR$/" + imlPath);
        modules.appendChild(module);
    }

    private String getImlPath(String moduleName) {
        Path imlFile = modulesDir.resolve(moduleName + ".iml");
        Path workspaceDir = ideaConfigDir.getParent();
        if (workspaceDir != null) {
            try {
                return workspaceDir.relativize(imlFile).toString().replace('\\', '/');
            } catch (IllegalArgumentException e) {
                // Fall through to default path
            }
        }
        return "modules/" + moduleName + ".iml";
    }
}
