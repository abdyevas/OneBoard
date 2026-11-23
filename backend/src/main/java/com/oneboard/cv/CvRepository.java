package com.oneboard.cv;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CvRepository extends JpaRepository<Cv, Long> {
    List<Cv> findAllByOrderByUpdatedAtDesc();
}
