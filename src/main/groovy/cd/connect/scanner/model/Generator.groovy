package cd.connect.scanner.model

import groovy.transform.ToString
import org.apache.maven.plugins.annotations.Parameter

@ToString
class Generator extends BaseTemplate {
	@Parameter
	List<String> sourceBases
	@Parameter
	boolean scanJarDependencies = false
	@Parameter
	List<Scan> scans
	@Parameter
	List<String> packages
	@Parameter
	List<Template> templates
	@Parameter
	String template

	@Parameter
	Map<String, String> context = [:]


	void resolveExtraPackages() {
		if (scans == null) {
			scans = []
		}

		packages?.each { String pkg ->
			Scan scan = new Scan()
			scan.setPackage(pkg)
			scan.resolvePackages()

			scans.add(scan)
		}
	}
}
