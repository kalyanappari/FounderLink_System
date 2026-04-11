package com.founderlink.wallet.serviceImpl;

import com.founderlink.wallet.command.WalletCommandService;
import com.founderlink.wallet.dto.request.WalletDepositRequestDto;
import com.founderlink.wallet.dto.response.WalletResponseDto;
import com.founderlink.wallet.query.WalletQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceImplTest {

    @Mock
    private WalletCommandService commandService;

    @Mock
    private WalletQueryService queryService;

    @InjectMocks
    private WalletServiceImpl walletService;

    @Test
    void createWallet_DelegatesToCommandService() {
        Long startupId = 1L;
        WalletResponseDto expected = new WalletResponseDto();
        when(commandService.createWallet(startupId)).thenReturn(expected);

        WalletResponseDto result = walletService.createWallet(startupId);

        assertEquals(expected, result);
        verify(commandService).createWallet(startupId);
    }

    @Test
    void depositFunds_DelegatesToCommandService() {
        WalletDepositRequestDto request = new WalletDepositRequestDto();
        WalletResponseDto expected = new WalletResponseDto();
        when(commandService.depositFunds(request)).thenReturn(expected);

        WalletResponseDto result = walletService.depositFunds(request);

        assertEquals(expected, result);
        verify(commandService).depositFunds(request);
    }

    @Test
    void getWalletByStartupId_DelegatesToQueryService() {
        Long startupId = 1L;
        WalletResponseDto expected = new WalletResponseDto();
        when(queryService.getWalletByStartupId(startupId)).thenReturn(expected);

        WalletResponseDto result = walletService.getWalletByStartupId(startupId);

        assertEquals(expected, result);
        verify(queryService).getWalletByStartupId(startupId);
    }

    @Test
    void getBalance_DelegatesToQueryService() {
        Long startupId = 1L;
        BigDecimal expected = BigDecimal.TEN;
        when(queryService.getBalance(startupId)).thenReturn(expected);

        BigDecimal result = walletService.getBalance(startupId);

        assertEquals(expected, result);
        verify(queryService).getBalance(startupId);
    }
}
