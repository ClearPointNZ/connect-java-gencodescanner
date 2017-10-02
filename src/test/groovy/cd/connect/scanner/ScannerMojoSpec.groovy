package cd.connect.scanner

import cd.connect.scanner.model.Generator
import cd.connect.scanner.model.Scan
import cd.connect.scanner.model.Template
import org.apache.maven.plugin.logging.Log
import org.apache.maven.project.MavenProject
import spock.lang.Specification

class ScannerMojoSpec extends Specification {
	String compilePath

	def setup() {
		compilePath = null
	}

	private ScannerMojo setupMojo(Generator gen) {
		ScannerMojo sm = new ScannerMojo(gen)
		sm.javaOutFolder = new File("target/test-output")
		sm.log = [info: { String msg -> println "DEBUG: $msg"}, debug: { String msg -> println msg},
		          warn: { String msg -> println "WARN: $msg"}, error: { String msg, Throwable t -> println "ERROR: ${msg}"; t.printStackTrace()}] as Log
		sm.project = [addCompileSourceRoot: { String path -> compilePath = path }, getBasedir: { return new File(".")}] as MavenProject
		sm.execute()

		return sm
	}

	def "webservlet scanning works expected"() {
		when: "I have setup a scanner"
		  Generator gen = new Generator(
			  scans: [
				  new Scan(joinGroup: "filters", packages: ["cd"], requiredAnnotations: ["WebFilter"]),
				  new Scan(joinGroup: "servlets", packages: ["cd"], requiredAnnotations: ["WebServlet"])
			  ],
			  template: "/web.mustache",
			  className: "com.WebModule",
			  sourceBases: ["./src/test/java"]
		  )
		and: "i kick it off"
		  ScannerMojo sm = setupMojo(gen)
		  String contents = new File(sm.javaOutFolder, "com/WebModule.java").text
		then: "the file contains the expected filters and servlets"
		  contents == '''Sample2Filtername=Sample2Filter,"peanuts",urlPatterns={ "/nuts/*", "groundnuts/*" }
SampleFiltername=SampleFilter,"simplefilter",initParams={ @WebInitParam(name = "sausage", value = "cumberlands"), @WebInitParam(name = "sausage2", value = "kielbasa", description = "yummy") },dispatcherType={ DispatcherType.ASYNC },urlPatterns="/*"
'''
			compilePath == sm.javaOutFolder.absolutePath
	}

	def "webservlet with shortform packages scanning works expected"() {
		when: "I have setup a scanner"
		Generator gen = new Generator(
			packages: ["com=filters/@WebFilter", "com=servlets/@WebServlet"],
			template: "/web.mustache",
			className: "com.WebModule",
			sourceBases: ["./src/test/java"]
		)
		and: "i kick it off"
		ScannerMojo sm = setupMojo(gen)
		String contents = new File(sm.javaOutFolder, "com/WebModule.java").text
		then: "the file contains the expected filters and servlets"
		contents == '''Sample2Filtername=Sample2Filter,"peanuts",urlPatterns={ "/nuts/*", "groundnuts/*" }
SampleFiltername=SampleFilter,"simplefilter",initParams={ @WebInitParam(name = "sausage", value = "cumberlands"), @WebInitParam(name = "sausage2", value = "kielbasa", description = "yummy") },dispatcherType={ DispatcherType.ASYNC },urlPatterns="/*"
'''
		compilePath == sm.javaOutFolder.absolutePath
	}

	def "shortform webservlet scanning works expected"() {
		when: "I have setup a scanner"
		Generator gen = new Generator(
			scans: [
				new Scan(packages: [
				  "com=filters/@WebFilter",
					"com=servlets/@WebServlet"
				]),
			],
			template: "/web.mustache",
			className: "com.WebModule",
			sourceBases: ["./src/test/java"]
		)
		and: "i kick it off"
		ScannerMojo sm = setupMojo(gen)
		String contents = new File(sm.javaOutFolder, "com/WebModule.java").text
		then: "the file contains the expected filters and servlets"
		contents == '''Sample2Filtername=Sample2Filter,"peanuts",urlPatterns={ "/nuts/*", "groundnuts/*" }
SampleFiltername=SampleFilter,"simplefilter",initParams={ @WebInitParam(name = "sausage", value = "cumberlands"), @WebInitParam(name = "sausage2", value = "kielbasa", description = "yummy") },dispatcherType={ DispatcherType.ASYNC },urlPatterns="/*"
'''
		compilePath == sm.javaOutFolder.absolutePath
	}

	private Generator injectGenerator() {
		Generator gen = new Generator(
			scans: [
				new Scan(joinGroup: "inject", packages: ["cd.connect.scanner:components"], followAnnotations: ["Inject"], limitToSource: true)
			],
			template: "/inject.mustache",
			className: "com.InjectModule",
			sourceBases: ["./src/test/java"]
		)
		
		return gen
	}

	def "inject scanning where limited to source generates injection for only this project"() {
		when: "I have setup a scanner"
			Generator gen = injectGenerator()
		and: "i kick it off"
		  ScannerMojo sm = setupMojo(gen)
			String contents = new File(sm.javaOutFolder, "com/InjectModule.java").text
		then: "it is correct"
		  contents == '''package com;
import cd.connect.scanner.components.Component1;
import cd.connect.scanner.components.Component2;
import cd.connect.scanner.components.Component3;

public class InjectModule {
  public void register() {
    register(Component1.class);
    register(Component2.class);
    register(Component3.class);
  }
}'''
		compilePath == sm.javaOutFolder.absolutePath
	}

	def "inject scanning where NOT limited to source generates injection for only this project"() {
		when: "I have setup a scanner"
			Generator gen = injectGenerator()
		  gen.scans[0].limitToSource = false
		and: "i kick it off"
			ScannerMojo sm = setupMojo(gen)
			String contents = new File(sm.javaOutFolder, "com/InjectModule.java").text
		then: "it is correct"
			contents == '''package com;
import cd.connect.scanner.components.Component1;
import cd.connect.scanner.components.Component2;
import cd.connect.scanner.components.Component3;
import java.lang.Integer;
import java.lang.String;

public class InjectModule {
  public void register() {
    register(Component1.class);
    register(Component2.class);
    register(Component3.class);
    register(Integer.class);
    register(String.class);
  }
}'''
		compilePath == sm.javaOutFolder.absolutePath
	}

	def "templates work as expected"() {
		when: "i set up the scanner for components and put them in a different group and define a template that maps"
			Generator gen = new Generator(
				scans: [
					new Scan(joinGroup: "sausage", packages: ["cd.connect.scanner:components"], followAnnotations: ["Inject"], limitToSource: true)
				],
				templates: [
				  new Template(joinGroups: ["inject=sausage"], className: "com.InjectModule", name: "sample", template: "/inject.mustache")
				],
				sourceBases: ["./src/test/java"]
			)
		and: "i kick it off"
			ScannerMojo sm = setupMojo(gen)
			def file = new File(sm.javaOutFolder, "com/InjectModule.java")
			file.delete()
			sm.execute()
			String contents = file.text
		then: "it is correct"
			contents == '''package com;
import cd.connect.scanner.components.Component1;
import cd.connect.scanner.components.Component2;
import cd.connect.scanner.components.Component3;

public class InjectModule {
  public void register() {
    register(Component1.class);
    register(Component2.class);
    register(Component3.class);
  }
}'''
			compilePath == sm.javaOutFolder.absolutePath
	}
}
