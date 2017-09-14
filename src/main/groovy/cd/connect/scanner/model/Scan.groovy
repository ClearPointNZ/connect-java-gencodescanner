package cd.connect.scanner.model

import groovy.transform.CompileStatic
import groovy.transform.ToString
import org.apache.maven.plugins.annotations.Parameter

import java.util.stream.Collectors

/**
 *
 * @author Richard Vowles - https://plus.google.com/+RichardVowles
 */
@CompileStatic
@ToString
class Scan {
	@Parameter
	List<String> joinGroups = []
	@Parameter
	private List<String> packages = []
	@Parameter
	boolean recursePackages = true
	@Parameter
	boolean limitToSource = true
	@Parameter
	boolean includeAbstract = false
	@Parameter
	List<String> classes = []
	@Parameter
	List<String> requiredAnnotations = []
	@Parameter
	List<String> followAnnotations = []
	@Parameter
	List<String> excludes
	@Parameter
	List<String> includes

	private List<SpecificPackageMap> specificPackageMaps = []

	/**
	 * there could be multiple packages that start with this package that we are scanning
	 * for different things for.
	 *
	 * @param pkg
	 * @return
	 */
	Set<SpecificPackageMap> getPackageMaps(String pkg) {
		pkg = pkg + '.' // prevent accidental matching
		return specificPackageMaps.stream().filter({ SpecificPackageMap spm ->
			return pkg.startsWith(spm.packageName)
		}).collect(Collectors.toSet())
	}

//	static void split(String pkg, Map<String, PackageSplitter> currentLevel, PackageSplitter parent) {
//		if (pkg.endsWith('.')) { // remove trailing .
//			pkg = pkg.substring(0, pkg.length()-1)
//		}
//
//		int dot = pkg.indexOf('.')
//		String part = (dot == -1) ? pkg : pkg.substring(0, dot)
//
//		PackageSplitter p = currentLevel[part]
//		p.parent = parent
//
//		if (dot != -1) {
//			split(pkg.substring(dot + 1), p.children, p)
//		}
//	}

	Collection<String> mostSpecificPackages = []

	private void resolveMostSpecificPackages() {
		List<SpecificPackageMap> packages = []
		packages.addAll(specificPackageMaps)
		packages.sort(new Comparator<SpecificPackageMap>() {
			@Override
			int compare(SpecificPackageMap o1, SpecificPackageMap o2) {
				return o1.packageName.compareTo(o2.packageName)
			}
		})
		List<String> prefixes = []

		packages.each { SpecificPackageMap spm ->
			if (!prefixes.find({spm.packageName.startsWith(it)})) {
				prefixes.add(spm.packageName)
			}
		}

		mostSpecificPackages = prefixes
	}

	void resolvePackages() {
		specificPackageMaps.each { SpecificPackageMap spm ->
			// if we only add joinGroups if groups aren't specified, then that means we can have a catch-group for
			// follow-annotations
			if (joinGroups && !spm.groups) { // if joinGroups are specified and subgroups aren't, add them? TODO: expected behaviour?
				spm.groups.addAll(joinGroups)
			}
		}

		resolveMostSpecificPackages()

//		println "most specific packages ${mostSpecificPackages}"
	}

	boolean interestingClass(String name) {
		return (!includes && !excludes) ||
		(includes && includes.contains(name)) ||
			(excludes && !excludes.contains(name))
	}

	void setJoinGroup(String group) {
		if (!this.joinGroups) {
			this.joinGroups = [group]
		}
	}

	void setPackage(String pkg) {
		if (!this.packages) {
			this.packages = []
		}

		parsePackage(pkg)
	}

	private void addPackageGroupMap(String pkg, Collection<String> requiredAnnotations, String... groupList) {
		if (!specificPackageMaps.find({it.packageName == pkg})) {
			SpecificPackageMap spm = new SpecificPackageMap()
			spm.packageName = pkg + "." // prevents accidental matching

			for(String g : groupList) {
				if (g == null) { continue }
				Arrays.stream(g.split(","))
					.map({ String s -> s.trim() })
				  .filter({ String s -> s.length() > 0 })
				  .forEach( { String s ->
						spm.groups.add(s)
				})
			}

			if (requiredAnnotations) {
				spm.requiredAnnotations.addAll(requiredAnnotations)
			}

			specificPackageMaps.addAll(spm)
		}
	}

	/**
	 * this gives us
	 * com.bluetrainsoftware=jersey, spring: resource=jersey2, resources=jersey3
	 * com.bluetrainsoftware=jersey, spring: resource=jersey2, resources
	 * com.bluetrainsoftware=jersey: resource, resources/@Path,@Provider
	 * com.bluetrainsoftware=jersey
	 * com.bluetrainsoftware: resource=jersey2, jersey3, resources=jersey3
	 * etc
	 *
	 * -r for recurse must apply to all otherwise it should be in a separate scan
	 * requireAnnotations applies to all otherwise it should be in a separate scan
	 *
	 * problems: the annotation is not resolved, it is string matched. If you had a provider from javax inject + jaxrs you would have
	 * problems. Its unlikely it would be both however.
	 *
	 * @param pkg
	 */
	private void parsePackage(String pkg) {
		int requiredAnnotationIdx = pkg.indexOf('/')
		Collection<String> requiredAnnotations = null
		if (requiredAnnotationIdx != -1) {
			requiredAnnotations = pkg.substring(requiredAnnotationIdx+1).split(',').toList().findResults({ String f ->
				f = f.trim()
				if (f && f.startsWith('@')) {
					f = f.substring(1)
				}
				return f ? f : null
			})

			pkg = pkg.substring(0, requiredAnnotationIdx)

			
		}
		if (pkg.contains(':')) {
			String basePackage = pkg.substring(0, pkg.indexOf(':')).trim()
			String packageGroupMap = null
			// allow com.bluetrainsoftware=jersey: resource, resources -> always map to those two (+ joinGroups)
			int groupIndex = basePackage.indexOf('=')
			if (groupIndex != -1) {
				packageGroupMap = basePackage.substring(groupIndex +1).trim()
				basePackage = basePackage.substring(0, groupIndex).trim()
			}
			if (basePackage.toLowerCase().endsWith("-r")) {
				recursePackages = false
				basePackage = basePackage.substring(0, basePackage.length() - 2)
			}
			Arrays.stream( pkg.substring(pkg.indexOf(':')+1).split(',')).map({ String s -> s.trim()})
			  .filter({ String s -> s.length() > 0})
			  .forEach({ String extra ->
				groupIndex = extra.indexOf('=')
				String extraGroups = null
				if (groupIndex != -1) {
					extra = extra.substring(0, groupIndex).trim()
					extraGroups = extra.substring(groupIndex+1).trim()
				}
				String pkgName = basePackage + '.' + extra
				this.packages.add(pkgName)
				
				addPackageGroupMap(pkgName, requiredAnnotations, packageGroupMap, extraGroups)
			})
		} else {
			int groupIndex = pkg.indexOf('=')
			String packageGroupMap = null
			if (groupIndex != -1) {
				packageGroupMap = pkg.substring(groupIndex +1).trim()
				pkg = pkg.substring(0, groupIndex).trim()
			}
			if (pkg.toLowerCase().endsWith("-r")) {
				recursePackages = false
				pkg = pkg.substring(0, pkg.length() - 2)
			}
			this.packages.add(pkg)
			addPackageGroupMap(pkg, requiredAnnotations, packageGroupMap)
		}
	}

	public void setPackages(List<String> pkgs) {
		if (!this.packages) {
			this.packages = []
		}

		pkgs?.each {
			parsePackage(it)
		}
	}

	/**
	 * scans:
	 * e.g. joinGroup: servlets
	 * packages: blah.servlets
	 * requiredAnnotations: WebServlet
	 *
	 * joinGroup: filters, springreg
	 * packages: blah.filters
	 * requiredAnnotations: WebFilter
	 *
	 * joinGroup: springreg
	 * packages: blah.servlet, blah.filters
	 * requiredAnnotations: WebServlet, WebFilter
	 * followAnnotations: Inject
	 * limitFollowToSource: true
	 *
	 * joinGroup: springreg
	 * packages: blah.resources
	 * followAnnotations: Inject
	 * limitFollowToSource: true
	 *
	 * joinGroup: springclient
	 * packages: blah.client
	 * limitToSource: false
	 * followAnnotations: Inject
	 * limitFollowToSource: false
	 *
	 * templates:
	 * template: springreg (/resource/blah.mustache)
	 * generatedClass: blah.module
	 * groups: springreg, filters, servlets
	 */
}
