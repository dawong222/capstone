package com.capstone.capstone.repository;

import com.capstone.capstone.entity.Constraints;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConstraintsRepository extends JpaRepository<Constraints, Long> {

    Optional<Constraints> findByStationId(Long stationId);

    @Query("SELECT c FROM Constraints c JOIN FETCH c.station")
    List<Constraints> findAllWithStation();
}
