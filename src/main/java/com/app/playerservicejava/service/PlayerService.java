package com.app.playerservicejava.service;

import com.app.playerservicejava.exception.PageOutOfRangeException;
import com.app.playerservicejava.model.Player;
import com.app.playerservicejava.repository.PlayerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PlayerService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerService.class);

    @Autowired
    private PlayerRepository playerRepository;

    public Page<Player> getPlayers(Pageable pageable) {
        Page<Player> page = playerRepository.findAll(pageable);

        int requestedPage = pageable.getPageNumber();

        boolean pageIsOutOfRange =
                (page.getTotalPages() > 0 &&
                        requestedPage >= page.getTotalPages())
                        ||
                        (page.getTotalPages() == 0 &&
                                requestedPage > 0);

        if (pageIsOutOfRange) { // Return 400 bad for out of range query
            throw new PageOutOfRangeException(
                    requestedPage,
                    page.getTotalPages()
            );
        }

        return page;
    }

    public Optional<Player> getPlayerById(String playerId) {
        Optional<Player> player = null;

        /* simulated network delay */
        try {
            player = playerRepository.findById(playerId);
            Thread.sleep((long)(Math.random() * 2000));
        } catch (Exception e) {
            LOGGER.error("message=Exception in getPlayerById; exception={}", e.toString());
            return Optional.empty();
        }
        return player;
    }

}
