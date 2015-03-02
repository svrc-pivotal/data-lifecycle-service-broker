package io.pivotal.cdm.repo;

import io.pivotal.cdm.model.BindingEntity;

import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@RepositoryRestResource(collectionResourceRel = "bindings", path = "bindings")
public interface BindingRepository extends
		PagingAndSortingRepository<BindingEntity, String> {

}
