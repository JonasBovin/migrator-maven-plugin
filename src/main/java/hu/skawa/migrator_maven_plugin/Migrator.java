package hu.skawa.migrator_maven_plugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;

/*
 * Copyright 2001-2005 The Apache Software Foundation. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may
 * obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by
 * applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 * the License for the specific language governing permissions and limitations under the License.
 */

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import com.google.common.hash.Hashing;
import com.google.common.io.Files;

import hu.skawa.migrator_maven_plugin.model.InternalDependency;

/**
 * Transform all dependencies for Bazel. Retrieves relevant information from the POM itself, and
 * uses the {@link ResolutionScope} TEST to scout all dependencies.
 * 
 * @author zmeggyesi
 */
@Mojo(
		name = "transform",
		defaultPhase = LifecyclePhase.PROCESS_SOURCES,
		requiresDependencyResolution = ResolutionScope.TEST)
public class Migrator extends AbstractMojo {
	
	@Parameter(required = true, defaultValue = "${project}")
	private MavenProject project;
	
	@Parameter(property = "outputFilePrefix")
	private String outputFilePrefix;
	
	@Parameter(property = "outputDirectives")
	private Boolean outputDirectives;
	
	@Parameter(property = "outputReferences")
	private Boolean outputReferences;
	
	@Parameter(property = "addHashes", defaultValue = "false")
	private Boolean addHashes;
	
	private List<InternalDependency> allDependencies = new ArrayList<InternalDependency>();
	
	public void execute() throws MojoExecutionException {
		Set<Artifact> artifacts = project.getArtifacts();
		for (Artifact arti : artifacts) {
			File file = arti.getFile();
			String hash = "";
			try {
				byte[] contents = Files.toByteArray(file);
				hash = Hashing.sha1().hashBytes(contents).toString();
			} catch (IOException e) {
				throw new MojoExecutionException("Dependency could not be hashed!", e);
			}
			InternalDependency id = new InternalDependency(arti.getGroupId(), arti
					.getArtifactId(), arti.getVersion(), hash);
			allDependencies.add(id);
			getLog().info(arti.getDownloadUrl());
			File remotes = new File(file.getParent() + File.separator + "_remote.repositories");
			try {
				getLog().info(Files.toString(remotes, StandardCharsets.UTF_8));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		try {
			if (outputFilePrefix != null) {
				if (outputDirectives) {
					File directives = new File(outputFilePrefix + "-directives");
					FileWriter writer = new FileWriter(directives);
					for (InternalDependency dep : allDependencies) {
						writer.write(dep.toBazelDirective(addHashes));
						writer.write("\n");
					}
					writer.close();
				}
				if (outputReferences) {
					File references = new File(outputFilePrefix + "-references");
					FileWriter writer = new FileWriter(references);
					for (InternalDependency dep : allDependencies) {
						writer.write(dep.getArtifactId() + ": @" + dep.getBazelName() + "//jar");
						writer.write("\n");
					}
					writer.close();
				}
			} else {
				for (InternalDependency dep : allDependencies) {
//					getLog().info(dep.toBazelDirective(addHashes));
				}
			}
		} catch (IOException e) {
			getLog().error(e);
		}
	}
}
