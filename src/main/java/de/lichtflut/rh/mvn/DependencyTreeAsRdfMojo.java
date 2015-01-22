package de.lichtflut.rh.mvn;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilderException;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.openrdf.model.Resource;
import org.openrdf.model.Statement;
import org.openrdf.model.impl.StatementImpl;
import org.openrdf.model.impl.URIImpl;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFWriter;
import org.openrdf.rio.n3.N3Writer;
import org.openrdf.rio.ntriples.NTriplesWriter;
import org.openrdf.rio.rdfxml.util.RDFXMLPrettyWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Goal that writes the dependency-tree as RDF.
 */
@Mojo( name = "write", defaultPhase = LifecyclePhase.PROCESS_SOURCES )
public class DependencyTreeAsRdfMojo extends AbstractMojo {

    public static final String PREDICATE_DEPENDS_ON = "http://arastreju.org/depends-on";

    // ----------------------------------------------------

    /**
     * The Maven project.
     */
    @Component
    private MavenProject project;

    /**
     * The dependency tree builder to use.
     */
    @Component( hint = "default" )
    private DependencyGraphBuilder dependencyGraphBuilder;

    /**
     * Location of the file.
     */
    @Parameter( defaultValue = "xml", property = "format", required = true )
    private String format;

    /**
     * Location of the file.
     */
    @Parameter( defaultValue = "${project.build.directory}/dependencies-rdf", property = "outputDir", required = true )
    private File outputDirectory;

    // ----------------------------------------------------

    public void execute() throws MojoExecutionException {
        File dir = outputDirectory;
        if ( !dir.exists() ) {
            dir.mkdirs();
        }
        final File target = new File(dir, "dependencies.rdf." + format);
        try (OutputStream out = new FileOutputStream(target)) {

            DependencyNode root = dependencyGraphBuilder.buildDependencyGraph(project, null);
            getLog().info("Writing RDF dependency information to: " + target);
            RDFWriter writer = getWriter(out);
            writer.startRDF();
            dump(root, writer);
            writer.endRDF();

        } catch (DependencyGraphBuilderException e) {
            throw new MojoExecutionException("Could not resolve dependencies.", e);
        } catch (RDFHandlerException e) {
            throw new MojoExecutionException("Could not write RDF.", e);
        } catch (IOException e) {
            throw new MojoExecutionException("IO error occurred.", e);
        }
    }

    // ----------------------------------------------------

    private void dump(DependencyNode node, RDFWriter writer) throws RDFHandlerException {
        Resource subject = toResource(node.getArtifact());
        for (DependencyNode child : node.getChildren()) {
            Resource object = toResource(child.getArtifact());
            writer.handleStatement(stmt(subject, PREDICATE_DEPENDS_ON, object));
            dump(child, writer);
        }
    }

    private RDFWriter getWriter(OutputStream out) throws MojoExecutionException {
        if ("xml".equals(format)) {
            return new RDFXMLPrettyWriter(out);
        } else if ("n3".equals(format)) {
            return new N3Writer(out);
        } else if ("ntriples".equals(format)) {
            return new NTriplesWriter(out);
        } else {
            throw new MojoExecutionException("No valid output RDF format: " + format);
        }

    }

    private Resource toResource(Artifact artifact) {
        String id = artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();
        return new URIImpl("http://arastreju.org/maven-artifact/" + id);
    }

    private Statement stmt(Resource subject, String predicate, String object) {
        return new StatementImpl(subject, new URIImpl(predicate), new URIImpl(object));
    }

    private Statement stmt(Resource subject, String predicate, Resource object) {
        return new StatementImpl(subject, new URIImpl(predicate), object);
    }
}
