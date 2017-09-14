package cd.connect.scanner.model

import cd.connect.scanner.collected.CollectedGroup
import groovy.transform.CompileStatic
import org.apache.maven.plugins.annotations.Parameter

@CompileStatic
class Template extends BaseTemplate {
	@Parameter
	String name
	@Parameter
	String template

	List<String> joinGroups = [];

	@Parameter
	boolean createGraph
	@Parameter
	boolean limitFollowToSource
	@Parameter
	Set<String> limitFollowPackages
	@Parameter
	List<String> postProcessors
	@Parameter
	Map<String, String> context = [:]

	public void setJoinGroup(String jg) {
		if (!joinGroups) {
			joinGroups = []
		}

		joinGroups.add(jg)
	}

	private void lineSplit(String group, Map<String, String> groupNames = [:]) {
		group.split("[;|,]").each { String map ->
			map = map.trim()
			
			if (map.contains('=')) {
				String[] fields = map.split('=')
				groupNames[fields[1]] = fields[0]
			} else {
				groupNames[map] = map
			}
		}
	}

	public Map<String, CollectedGroup> exportGroups(Map<String, CollectedGroup> existing) {
		Map<String, CollectedGroup> map = [:]

		Map<String, String> groupNames = [:]

		if (joinGroups) {
			joinGroups.each { String gp ->
				lineSplit(gp, groupNames)
			}

			existing.keySet().each { String key ->
				if (groupNames[key]) { // allow mapping from one to another
					map[groupNames[key]] = existing[key]
				}
			}
		}

		return map
	}
}
