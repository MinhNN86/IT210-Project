package com.projectit210.repository;

import com.projectit210.entity.AcademicEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AcademicEvaluationRepository extends JpaRepository<AcademicEvaluation, Long> {

    Optional<AcademicEvaluation> findByMentoringSessionId(Long mentoringSessionId);

    @Query("SELECT e FROM AcademicEvaluation e " +
           "JOIN FETCH e.mentoringSession s " +
           "JOIN FETCH e.lecturer l " +
           "JOIN FETCH l.user " +
           "JOIN FETCH l.department " +
           "WHERE e.student.id = :studentId " +
           "ORDER BY s.sessionDate DESC")
    List<AcademicEvaluation> findByStudentIdWithDetails(@Param("studentId") String studentId);
}
