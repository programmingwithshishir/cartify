package com.cartify.store;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemRepository extends JpaRepository<Item, Long> {
    List<Item> findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCaseOrCategoryTagsContainingIgnoreCase(
            String nameQuery,
            String descriptionQuery,
            String tagsQuery);
}
