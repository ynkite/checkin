package idusw.sbb.triplinker.domain.place.repository;

import idusw.sbb.triplinker.domain.place.entity.Place;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    // AI JSON 파싱 시 사용 (address 없을 때)
    Optional<Place> findByName(String name);

    // address 확보 후 정밀 중복 판단
    Optional<Place> findByNameAndAddress(String name, String address);

    // external_link 기준 중복 판단
    Optional<Place> findByExternalLink(String externalLink);
}