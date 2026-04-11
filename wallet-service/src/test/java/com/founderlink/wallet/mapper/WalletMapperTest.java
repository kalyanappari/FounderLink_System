package com.founderlink.wallet.mapper;
 
import com.founderlink.wallet.dto.response.WalletResponseDto;
import com.founderlink.wallet.entity.Wallet;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class WalletMapperTest {

    private final WalletMapper walletMapper = new WalletMapper();

    @Test
    void toResponseDto_Success() {
        Wallet wallet = new Wallet();
        wallet.setId(1L);
        wallet.setStartupId(100L);
        wallet.setBalance(BigDecimal.valueOf(1000));
        wallet.setCreatedAt(LocalDateTime.now());
        wallet.setUpdatedAt(LocalDateTime.now());

        WalletResponseDto dto = walletMapper.toResponseDto(wallet);

        assertNotNull(dto);
        assertEquals(wallet.getId(), dto.getId());
        assertEquals(wallet.getStartupId(), dto.getStartupId());
        assertEquals(wallet.getBalance(), dto.getBalance());
        assertEquals(wallet.getCreatedAt(), dto.getCreatedAt());
        assertEquals(wallet.getUpdatedAt(), dto.getUpdatedAt());
    }

    @Test
    void toResponseDto_NullInput() {
        assertNull(walletMapper.toResponseDto(null));
    }
}
