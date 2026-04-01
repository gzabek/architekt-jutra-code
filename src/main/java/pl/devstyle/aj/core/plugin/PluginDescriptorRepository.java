package pl.devstyle.aj.core.plugin;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PluginDescriptorRepository extends JpaRepository<PluginDescriptor, String> {

    List<PluginDescriptor> findByEnabledTrue();
}
