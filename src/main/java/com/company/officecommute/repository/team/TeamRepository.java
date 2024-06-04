package com.company.officecommute.repository.team;

import com.company.officecommute.domain.team.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {
    // TODO optional 로
    //Team findByName(String teamName);

    Optional<Team> findByName(String teamName);

    @Query("""
            select new Team(t.name, t.managerName, t.memberCount)
            from Team t
            """)
    List<Team> findTeam();
}
