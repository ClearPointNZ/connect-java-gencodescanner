package cd.connect.scanner

import cd.connect.scanner.collected.CollectedClass
import cd.connect.scanner.collected.CollectedGroup
import cd.connect.scanner.model.Generator
import cd.connect.scanner.model.Scan
import cd.connect.scanner.model.SpecificPackageMap
import com.github.javaparser.JavaParser
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.Modifier
import com.github.javaparser.ast.body.FieldDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.body.TypeDeclaration
import com.github.javaparser.ast.expr.AnnotationExpr
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserClassDeclaration
import com.github.javaparser.symbolsolver.model.declarations.ReferenceTypeDeclaration
import com.github.javaparser.symbolsolver.model.methods.MethodUsage
import com.github.javaparser.symbolsolver.model.typesystem.ReferenceType
import com.github.javaparser.symbolsolver.model.typesystem.Type
import com.github.javaparser.symbolsolver.reflectionmodel.ReflectionClassDeclaration
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver
import com.samskivert.mustache.Mustache
import com.samskivert.mustache.Template
import groovy.transform.CompileStatic
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.*
import org.apache.maven.project.MavenProject
import org.apache.maven.project.MavenProjectHelper

import java.lang.annotation.Annotation

/**
 *
 * @author Richard Vowles - https://plus.google.com/+RichardVowles
 */
@CompileStatic

@Mojo(name = "generate-sources",
	defaultPhase = LifecyclePhase.GENERATE_SOURCES,
	configurator = "include-project-dependencies",
	requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
class ScannerMojo extends AbstractMojo {
	protected Map<String, CollectedGroup> groups = new HashMap<String, CollectedGroup>().withDefault { String key ->
		return new CollectedGroup(name: key)
	}
	private JavaParserFacade facade
	private CombinedTypeSolver combinedTypeSolver

	@Component
	protected MavenProjectHelper projectHelper;

	@Parameter(required = true, property = "scanner")
	protected Generator scanner

	@Parameter(property = 'project', readonly = true)
	protected MavenProject project

	@Parameter(defaultValue = '${project.build.directory}/generated-sources/modules/')
	File javaOutFolder

	private final String TARGET_CLASSES = File.separator + "target" + File.separator + "classes"
	private final String TARGET_CLASSES2 = File.separator + "target" + File.separator + "classes" + File.separator

	public ScannerMojo() {}

	public ScannerMojo(Generator scanner) {
		this.scanner = scanner
	}

	/**
	 * go figure out what our classpath is, including the dependencies of this project
	 */
	private void createResolver() {
		combinedTypeSolver = new CombinedTypeSolver()
		combinedTypeSolver.add(new ReflectionTypeSolver())

		// add any extra ones we have been given
		scanner.sourceBases?.each { String sourceBase ->
			combinedTypeSolver.add(new JavaParserTypeSolver(new File(sourceBase)))
		}

		// now check if we have a url classpath
		if (getClass().classLoader instanceof URLClassLoader) {
			URLClassLoader urlClassLoader = URLClassLoader.class.cast(getClass().classLoader)

			urlClassLoader.URLs.each { URL url ->
				String sUrl = url.toString()

				if ( sUrl.startsWith("jar:")) {
					println "jar -> " + sUrl
					combinedTypeSolver.add(new JarTypeSolver(sUrl.substring(4)))
				} else if (sUrl.startsWith("file:")) {
					if (sUrl.endsWith(".jar")) {
						getLog().debug("jarfile -> " + sUrl)
						combinedTypeSolver.add(new JarTypeSolver(new File(url.toURI()).absolutePath))
					} else if (sUrl.endsWith(TARGET_CLASSES) || sUrl.endsWith(TARGET_CLASSES2)) {
						File realFolder = new File(new File(url.toURI()).getParentFile().getParentFile(), "/src/main/java")

						if (realFolder.exists()) {
							getLog().debug("dependent project -> " + realFolder.absolutePath)

							combinedTypeSolver.add(new JavaParserTypeSolver(realFolder))
						}
					} else {
						combinedTypeSolver.add(new JavaParserTypeSolver(new File(url.toURI())))
						getLog().debug("file -> " + sUrl)
					}
				} else {
					getLog().debug("no idea -> " + sUrl)
				}
			}
		} else {
			getLog().warn("not using a url classloader, cannot discover classes.")
		}
	}

	protected void checkSourceBasesAreSetAndHaveCorrectOffsets() {
		if (scanner.sourceBases == null) {
			scanner.sourceBases = project.getCompileSourceRoots()
		} else {
			String projectPath = project.basedir.absolutePath

			scanner.sourceBases = scanner.sourceBases.collect({String sb -> sb.startsWith(projectPath) ? sb : new File(project.basedir, sb).absolutePath})
		}
	}

	@Override
	void execute() throws MojoExecutionException, MojoFailureException {
		checkSourceBasesAreSetAndHaveCorrectOffsets()

		scanner.resolveExtraPackages()

		if (!scanner.scans) {
			getLog().warn("No scans, source base, bailing. ${scanner}")
			return
		}

		getLog().info("Base project directory for scanning is ${project?.basedir?.absolutePath}")

		createResolver()

		facade = JavaParserFacade.get(combinedTypeSolver)

		scanner.scans.each { Scan scan ->
			scan.resolvePackages()
		}

		scanner.scans.each { Scan scan ->
			scan.mostSpecificPackages.each { String pkg ->
				scanner.sourceBases.each { String sourceBase ->
					File dir = new File(new File(sourceBase), pkg.replace('.', File.separator));

					if (dir.exists()) {
						processFiles(scan, dir)
					} else {
						getLog().debug("${dir.path} does not exist - ignoring")
					}
				}
			}
		}

		groups.keySet().each { String gp ->
			def group = groups[gp]
			if (group.classTypes?.size() == 0 && group.types?.size() == 0) {
				getLog().warn("Group ${gp} did not discover any classes, is that what you expected?")
			} else {
				getLog().info("Group `${gp}` src:${group.types*.name}, classes:${group.classTypes*.name}")
			}
		}

		processTemplates()

		project.addCompileSourceRoot(javaOutFolder.absolutePath)
	}

	private void processTemplates() {
		Map<String, Object> context = [:]

		if (scanner.templates) {
			getLog().info("processing ${scanner.templates.size()} templates.")
		} else {
			getLog().info("there are no templates, will look for default top level template")
		}

		scanner.templates?.each { cd.connect.scanner.model.Template template ->
			if (template.template && template.className) {
				Map<String, CollectedGroup> mappedNames = template.exportGroups(groups)

				if (mappedNames) {
					if (template.context) {
						context.putAll(template.context)
					}

					context.putAll(mappedNames)
					context.put("packageName", template.packageName)
					context.put("simpleName", template.simpleName)
					context.put("className", template.className)

					writeTemplate(context, template.template, template.className)
				} else {
					getLog().warn("No groups collected data that the template ${template.name} relies on (joinGroups ${template.joinGroups})")
				}
			} else {
				getLog().warn("Found template ${template.name} but could not run it (no template or classname)")
			}
		}

		List<CollectedGroup> allGroups = []
		allGroups.addAll(groups.values())
		if (scanner.className && scanner.template && allGroups) {
			context.put("packageName", scanner.packageName)
			context.put("simpleName", scanner.simpleName)
			context.put("className", scanner.className)

			if (scanner.context) {
				context.putAll(scanner.context)
			}

			allGroups.each { CollectedGroup group ->
				context.put(group.name, group)
			}

			writeTemplate(context, scanner.template, scanner.className)
		} else if (allGroups) {
			getLog().warn("Ignoring groups ${allGroups*.name} because no template/classname exists at group or top level.")
		}
	}

	private void writeTemplate(Map<String, Object> context, String template, String className) {
		Template mapperTemplate = Mustache.compiler().compile(new InputStreamReader( getTemplate(template)));
		Writer writer = createWriter(className)
		mapperTemplate.execute(context, writer)
		writer.close()
		context.clear()
	}

	private Writer createWriter(String className) {
		File outFile = new File(javaOutFolder, className.replace('.', File.separator) + ".java")
		outFile.parentFile.mkdirs()
		getLog().info("writing ${outFile.absolutePath}")
		return new FileWriter(outFile)
	}

	private InputStream getTemplate(String name) throws MojoExecutionException {
		InputStream stream = getClass().getResourceAsStream(name);

		if (stream == null) {
			if (!name.startsWith(File.separator)) {
				name = File.separator + name;
			}
			// try local project directory src/main/resources
			File f = new File(project.basedir, "src/main/resources" + name);
			getLog().info("looking in " + f.getAbsolutePath());
			if (f.exists()) {
				try {
					stream = new FileInputStream(f);
				} catch (FileNotFoundException e) { // hard to see how this can happen
					throw new MojoExecutionException("Cannot find file", e);
				}
			}
		}

		if (stream == null) {
			// try local project directory src/test/resources
			File f = new File(project.basedir, "src/test/resources" + name);
			if (f.exists()) {
				try {
					stream = new FileInputStream(f);
				} catch (FileNotFoundException e) { // hard to see how this can happen
					throw new MojoExecutionException("Cannot find file", e);
				}
			}
		}

		if (stream == null) {
			throw new MojoExecutionException("Cannot find resource named " + name);
		}

		return stream;
	}

	void processFiles(Scan scan, File file) {
		getLog().info("scanning ${file.absolutePath}: ${scan.recursePackages}")
		file.listFiles().each { File f ->
			if (f.isDirectory() && scan.recursePackages) {
				processFiles(scan, f)
			} else if (f.name.endsWith(".java")) {
				processFile(scan, f);
			}
		}
	}

	// ensure we get the same compilation unit for the same same file across different scan requests
	Map<File, CompilationUnit> sourceCache = new HashMap<File, CompilationUnit>().withDefault { File f ->
		println "trying file ${f.absolutePath}"
		return JavaParser.parse(f)
	}


	private boolean processFile(Scan scan, File file) {
		if (!file.exists()) {
			return false
		}
		
		CompilationUnit cu = sourceCache[file];

		if (cu.types == null) { // nothing in this file
			return false;
		}

		try {
			for (TypeDeclaration td : cu.types) {
				if (!scan.includeAbstract && td.modifiers.contains(Modifier.ABSTRACT)) {
					continue
				}

				String name = td.name.toString()

				ReferenceTypeDeclaration rd = facade.getTypeDeclaration(td)

				if ((rd.isClass() || rd.isInterface()) && scan.interestingClass(name)) {
					CollectedClass cc = new CollectedClass(rd, td)

					if (scan.requiredAnnotations) {
						cc.setOriginalAnnotation(findSourceAnnotation(td, scan.requiredAnnotations))
					}

					if (!scan.requiredAnnotations || cc.annotation) {
						// this is an interesting class, therefore it should go in all of the groups outlined by the scan
						addTypeToGroups(scan, cc)

						if (scan.followAnnotations) {
							discoverFollowingAnnotations(rd, td, scan)
						}
					}
				}
			}
		} catch (Exception ex) {
			getLog().error("Failed", ex)
		}

		return true
	}

	private AnnotationExpr findSourceAnnotation(TypeDeclaration td, Collection<String> requiredAnnotations) {
		for(AnnotationExpr annotation : td.annotations) {
			if (requiredAnnotations.contains(annotation.name.asString())) {
				return annotation
			}
		}

		return null
	}

	private addTypeToGroups(Scan scan, CollectedClass cc) {
		scan.getPackageMaps(cc.packageName).each { SpecificPackageMap spm ->
			if (spm.requiredAnnotations) {
				AnnotationExpr annotation = findSourceAnnotation(cc.src, spm.requiredAnnotations)
				if (!annotation) {
					return // skip each
				} else {
					cc.addOriginalAnnotation(annotation)
				}
			}

			spm.groups.each { String group ->
				addTypeToGroup(group, cc, null)
			}
		}
	}

	private addTypeToGroups(Scan scan, ReferenceType td) {
		CollectedClass cc = null
		Class<?> clazz = null
		String packageName

		if (td.getTypeDeclaration() instanceof JavaParserClassDeclaration) {
			cc = new CollectedClass(td.getTypeDeclaration(), JavaParserClassDeclaration.class.cast(td.getTypeDeclaration()).getWrappedNode())
			packageName = td.getTypeDeclaration().packageName
		} else if (!scan.limitToSource && td.getTypeDeclaration() instanceof ReflectionClassDeclaration) {
			clazz = Class.forName(td.describe(), false, this.getClass().getClassLoader())
			packageName = clazz.name
			packageName = packageName.substring(0, packageName.lastIndexOf('.')) // chop off class name
		} else {
			return
		}

		Set<SpecificPackageMap> packageMaps = scan.getPackageMaps(packageName)

		packageMaps?.each { SpecificPackageMap spm ->
			if (spm.requiredAnnotations) {
				if (cc) {
					AnnotationExpr ann = findSourceAnnotation(cc.src, spm.requiredAnnotations)
					if (ann == null) {
						return // skip because source does not have annotation
					} else {
						cc.addOriginalAnnotation(ann)
					}
				} else if (clazz && !hasRequiredAnnotations(clazz, spm.requiredAnnotations)) {
					return // skip because class doesn't have annotation
				}
			}

			spm.groups.each { String group ->
				addTypeToGroup(group, cc, clazz)
			}
		}

		if (!packageMaps && scan.joinGroups) {
			// this is the fall through for classes that are annotation mapped but are not in the hierarchy of specified
			// packages. i.e. if they are injected and come from a package outside the one you are scanning, they drop into
			// the groups specified here.
			scan.joinGroups.each { String group ->
				addTypeToGroup(group, cc, clazz)
			}
		}
	}

	private void addTypeToGroup(String group, CollectedClass cc, Class<?> clazz) {
		CollectedGroup group1 = groups[group]

		if (cc) {
			CollectedClass existingCC = group1.types.find({CollectedClass it -> it.equals(cc)})
			if (existingCC && cc.annotation) {
				existingCC.addAnnotation(cc.annotation)
			} else if (!existingCC) {
				group1.types.add(cc)
			}
		} else if (clazz && !group1.classTypes.contains(clazz)) {
			group1.classTypes.add(clazz)
		}

	}


	private void discoverFollowingAnnotations(ReferenceTypeDeclaration rd, TypeDeclaration td, Scan scan) {
		List<String> fields = []
		List<String> members = []

		td.members.each { member ->
			if (member instanceof FieldDeclaration) {
				FieldDeclaration fld = FieldDeclaration.class.cast(member)
				if (scan.followAnnotations.any { String annotation -> fld.isAnnotationPresent(annotation)}) {
					fields.add(fld.variables[0].name.asString())
				}
			} else if (member instanceof MethodDeclaration) {
				MethodDeclaration method = MethodDeclaration.class.cast(member)
				if (scan.followAnnotations.any({ String annotation -> method.isAnnotationPresent(annotation)})) {
					members.add(method.name.asString())
				}
			}
		}

		fields.each { String name ->
			com.github.javaparser.symbolsolver.model.declarations.FieldDeclaration fld = rd.getField(name)
			if (fld.type instanceof ReferenceType) {
				addTypeToGroups(scan, ReferenceType.class.cast(fld.type))
			}
		}

		rd.getAllMethods().each { MethodUsage mu ->
			if (members.contains(mu.name)) {
				if (mu.paramTypes) {
					mu.paramTypes.each { Type t ->
						if (t instanceof ReferenceType) {
							addTypeToGroups(scan, ReferenceType.class.cast(t))
						}
					}
				}
			}
		}
	}

	private static boolean hasRequiredAnnotations(TypeDeclaration td, Collection<String> annotations) {
		return annotations.any { String annotation -> td.isAnnotationPresent(annotation)}
	}

	private static boolean hasRequiredAnnotations(Class<?> clazz, Collection<String> annotations) {
		return clazz.annotations.any({ Annotation a -> annotations.contains(a.annotationType().simpleName)})
	}
}
