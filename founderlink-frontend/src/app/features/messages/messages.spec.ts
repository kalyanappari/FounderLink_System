import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { MessagesComponent } from './messages';
import { AuthService } from '../../core/services/auth.service';
import { MessagingService } from '../../core/services/messaging.service';
import { UserService } from '../../core/services/user.service';
import { ThemeService } from '../../core/services/theme.service';
import { ActivatedRoute } from '@angular/router';
import { of, throwError, Subject } from 'rxjs';
import { vi } from 'vitest';

describe('MessagesComponent', () => {
  let component: MessagesComponent;
  let fixture: ComponentFixture<MessagesComponent>;
  let messagingSpy: any;
  let userSpy: any;
  let authSpy: any;

  beforeEach(async () => {
    messagingSpy = {
      getPartnerIds: vi.fn(),
      getConversation: vi.fn(),
      sendMessage: vi.fn()
    };
    userSpy = {
      getUser: vi.fn(),
      getAllUsers: vi.fn()
    };
    authSpy = {
      userId: vi.fn().mockReturnValue(1)
    };

    const routeStub = {
      snapshot: { queryParamMap: { get: vi.fn().mockReturnValue(null) } }
    };

    await TestBed.configureTestingModule({
      imports: [MessagesComponent],
      providers: [
        { provide: AuthService, useValue: authSpy },
        { provide: MessagingService, useValue: messagingSpy },
        { provide: UserService, useValue: userSpy },
        { provide: ThemeService, useValue: { isCrystal: () => false } },
        { provide: ActivatedRoute, useValue: routeStub }
      ]
    }).compileComponents();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('Init and Loading Partners', () => {
    beforeEach(() => {
      messagingSpy.getPartnerIds.mockReturnValue(of({ data: [2, 3] }));
      userSpy.getUser.mockImplementation((id: number) => {
        if (id === 2) return of({ data: { userId: 2, name: 'Alice' } });
        if (id === 3) return of({ data: { userId: 3, name: 'Bob' } });
        if (id === 99) return of({ data: { userId: 99, name: 'Zeke' } });
        return of(null);
      });
      messagingSpy.getConversation.mockReturnValue(of({ data: [] }));
    });

    it('should load partners properly without a target user ID query', async () => {
      fixture = TestBed.createComponent(MessagesComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      
      await new Promise(r => setTimeout(r, 0)); // Flush Promise.all

      expect(component.partners().length).toBe(2);
      expect(component.partners()[0].name).toBe('Alice');
      expect(component.selectedPartner()?.userId).toBe(2);
      expect(component.loading()).toBe(false);
    });

    it('should navigate to target user explicitly defined via route query map', async () => {
      // Mock the route param before detectChanges/ngOnInit
      const route = TestBed.inject(ActivatedRoute);
      vi.spyOn(route.snapshot.queryParamMap, 'get').mockReturnValue('3');

      fixture = TestBed.createComponent(MessagesComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      
      await new Promise(r => setTimeout(r, 0));

      expect(component.selectedPartner()?.userId).toBe(3);
      expect(component.partners().some(p => p.name === 'Bob')).toBe(true);
    });

    it('should fetch target user externally if they are not in the existing partner memory map', async () => {
      messagingSpy.getPartnerIds.mockReturnValue(of({ data: [2] }));
      const route = TestBed.inject(ActivatedRoute);
      vi.spyOn(route.snapshot.queryParamMap, 'get').mockReturnValue('99');

      fixture = TestBed.createComponent(MessagesComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      
      await new Promise(r => setTimeout(r, 0));

      expect(component.selectedPartner()?.userId).toBe(99);
      expect(component.selectedPartner()?.name).toBe('Zeke');
      expect(component.partners().length).toBe(2);
    });

    it('should capture a failing conversational matrix map gracefully', async () => {
      messagingSpy.getPartnerIds.mockReturnValue(throwError(() => ({ error: 'Failed' })));
      
      fixture = TestBed.createComponent(MessagesComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      expect(component.errorMsg()).toBe('Failed to load conversations.');
      expect(component.loading()).toBe(false);
    });

    it('should handle empty partner IDs list cleanly', async () => {
      messagingSpy.getPartnerIds.mockReturnValue(of({ data: [] }));
      fixture = TestBed.createComponent(MessagesComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      
      await new Promise(r => setTimeout(r, 0));
      expect(component.loading()).toBe(false);
      expect(component.partners().length).toBe(0);
    });
  });

  describe('Chat selection and Live Polling Mechanics', () => {
    beforeEach(() => {
      messagingSpy.getPartnerIds.mockReturnValue(of({ data: [] }));
      userSpy.getUser.mockReturnValue(of({ data: null }));
      messagingSpy.getConversation.mockReturnValue(of({ data: [], totalElements: 10 }));
      
      fixture = TestBed.createComponent(MessagesComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
    });

    it('should set an interval up when a user is selected then clean it', () => {
      vi.useFakeTimers();
      
      component.selectPartner({ userId: 9, name: 'T', email: '', lastMessage: '', lastMessageTime: '' });
      expect(component.selectedPartner()?.userId).toBe(9);
      expect(messagingSpy.getConversation).toHaveBeenCalledWith(9, 0);
      
      // Tick polling
      messagingSpy.getConversation.mockClear();
      vi.advanceTimersByTime(2100);
      
      expect(messagingSpy.getConversation).toHaveBeenCalledWith(9, 0); // Polling calls `false` normally

      // Cleanup
      component.ngOnDestroy();
      messagingSpy.getConversation.mockClear();
      vi.advanceTimersByTime(2100);
      expect(messagingSpy.getConversation).not.toHaveBeenCalled();

      vi.useRealTimers();
    });

    it('should append new incoming messages properly and avoid duplicates', () => {
      // Mock existing
      component.messages.set([
        { id: 1, senderId: 1, recipientId: 2, content: 'Hey', createdAt: '2023-01-01' } as any
      ]);
      
      // incoming will be reversed within component
      messagingSpy.getConversation.mockReturnValue(of({
        data: [
          { id: 2, senderId: 2, recipientId: 1, content: 'Hi', createdAt: '2023-01-02' },
          { id: 1, senderId: 1, recipientId: 2, content: 'Hey', createdAt: '2023-01-01' } // dupe
        ],
        totalElements: 5
      }));

      // Simulate a page 0 call (poll or load)
      component.loadMessages(2, 0, false);
      
      // Expect arrays to combine uniquely
      expect(component.messages().length).toBe(2);
      expect(component.messages()[1].content).toBe('Hi'); // because incoming reversed order is chronological
    });

    it('should prepend on page older messages fetch', () => {
      component.selectedPartner.set({ userId: 2 } as any);
      component.messages.set([ { id: 100, senderId: 1, recipientId: 2, content: 'Recent', createdAt: '2023-01-02' } as any ]);
      
      messagingSpy.getConversation.mockReturnValue(of({
        data: [ { id: 99, senderId: 2, recipientId: 1, content: 'Older', createdAt: '2023-01-01' } ],
        totalElements: 20
      }));

      component.loadOlderMessages();
      
      expect(component.currentChatPage()).toBe(1);
      expect(messagingSpy.getConversation).toHaveBeenCalledWith(2, 1);
      expect(component.messages().length).toBe(2);
      expect(component.messages()[0].content).toBe('Older'); // History at top
    });

    it('should handle loadMessages fetch errors', () => {
      messagingSpy.getConversation.mockReturnValue(throwError(() => ({ error: 'Fail Chat' })));
      component.loadMessages(2, 0);
      expect(component.errorMsg()).toBe('Failed to load messages.');
    });

    it('should abort loadOlderMessages if no partner selected', () => {
      component.selectedPartner.set(null);
      component.loadOlderMessages();
      expect(messagingSpy.getConversation).not.toHaveBeenCalled();
    });
  });

  describe('Message submission', () => {
    beforeEach(() => {
      messagingSpy.getPartnerIds.mockReturnValue(of({ data: [] }));
      fixture = TestBed.createComponent(MessagesComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
    });

    it('should guard against bad submits', () => {
      component.selectedPartner.set(null);
      component.messageContent = '  ';
      component.sendMessage();
      expect(component.errorMsg()).toContain('empty');
      
      component.messageContent = 'Valid';
      component.sendMessage();
      expect(component.errorMsg()).toContain('select a conversation');

      expect(messagingSpy.sendMessage).not.toHaveBeenCalled();
    });

    it('should blast valid submission successfully via api', () => {
      component.selectedPartner.set({ userId: 8 } as any);
      component.messageContent = 'Hey team';
      
      const newMsg = { id: 99, senderId: 1, recipientId: 8, content: 'Hey team', createdAt: '' };
      messagingSpy.sendMessage.mockReturnValue(of({ data: newMsg }));

      component.sendMessage();

      expect(messagingSpy.sendMessage).toHaveBeenCalledWith(8, 'Hey team');
      expect(component.messageContent).toBe('');
      expect(component.sendingMessage()).toBe(false);
      
      expect(component.messages()[0].id).toBe(99);
    });

    it('should log an error when messaging service fails aggressively', () => {
      component.selectedPartner.set({ userId: 8 } as any);
      component.messageContent = 'Fail test';
      
      messagingSpy.sendMessage.mockReturnValue(throwError(() => ({ error: 'Simulated Crash' })));

      component.sendMessage();

      expect(component.sendingMessage()).toBe(false);
      expect(component.errorMsg()).toBe('Simulated Crash');
    });
  });

  describe('Discovery mechanics', () => {
    beforeEach(() => {
      messagingSpy.getPartnerIds.mockReturnValue(of({ data: [] }));
      messagingSpy.getConversation.mockReturnValue(of({ data: [] }));
      fixture = TestBed.createComponent(MessagesComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
    });

    it('should fetch directory exactly once lazily upon toggling UI', () => {
      userSpy.getAllUsers.mockReturnValue(of({ data: [{ userId: 5 }, { userId: 1 }] })); // 1 is me (mocked)
      component.showUserSelector.set(false);

      component.loadAllUsers();
      
      expect(component.allUsers().length).toBe(1);
      expect(component.allUsers()[0].userId).toBe(5);
      expect(component.showUserSelector()).toBe(true);

      // Verify redundant call ignores API fetch
      userSpy.getAllUsers.mockClear();
      component.loadAllUsers();
      expect(userSpy.getAllUsers).not.toHaveBeenCalled();
      expect(component.showUserSelector()).toBe(false); // Validates toggle logic works repeatedly regardless
    });

    it('should add completely new partner cleanly to interface', () => {
      const u = { userId: 9, email: 'z@b.com', name: 'Zorro' };
      component.startConversationWith(u as any);
      
      expect(component.partners()[0].name).toBe('Zorro');
      expect(component.selectedPartner()?.userId).toBe(9);
    });
  });

  describe('Template UI Verification', () => {
    it('should show loading spinner when loading is true', async () => {
      // Use Subject to keep it in loading state
      const subject = new Subject();
      messagingSpy.getPartnerIds.mockReturnValue(subject);
      
      fixture = TestBed.createComponent(MessagesComponent);
      component = fixture.componentInstance;
      // ngOnInit will hang on getPartnerIds
      fixture.detectChanges(); 
      
      const spinner = fixture.nativeElement.querySelector('.spinner');
      expect(spinner).toBeTruthy();
    });

    it('should show empty state message when no partners exist', async () => {
      messagingSpy.getPartnerIds.mockReturnValue(of({ data: [] }));
      fixture = TestBed.createComponent(MessagesComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      await new Promise(r => setTimeout(r, 0));
      fixture.detectChanges();
      
      const emptyMsg = fixture.nativeElement.querySelector('.conversations-list .empty-small');
      expect(emptyMsg.textContent).toContain('No conversations');
    });

    it('should show error alert when errorMsg is present', async () => {
      messagingSpy.getPartnerIds.mockReturnValue(of({ data: [] }));
      fixture = TestBed.createComponent(MessagesComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      await new Promise(r => setTimeout(r, 0));
      
      // Error alert for sending is inside selectedPartner block
      component.selectedPartner.set({ userId: 1, name: 'T' } as any);
      component.errorMsg.set('Network Error');
      fixture.detectChanges();
      
      const alert = fixture.nativeElement.querySelector('.alert-error');
      expect(alert).toBeTruthy();
      expect(alert.textContent).toContain('Network Error');
    });
  });

  describe('DOM format logic triggers', () => {
    beforeEach(() => {
      messagingSpy.getPartnerIds.mockReturnValue(of({ data: [] }));
      fixture = TestBed.createComponent(MessagesComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
    });

    it('should confirm currentUser', () => {
      expect(component.isCurrentUser(1)).toBe(true);
      expect(component.isCurrentUser(2)).toBe(false);
    });

    it('should format date and time strings accurately', () => {
      const stamp = '2023-01-01T15:30:00Z';
      expect(component.formatDate(stamp)).toBeTruthy();
      expect(component.formatTime(stamp)).toBeTruthy();
    });

    it('should execute view checked gracefully despite no ref mock limitations', () => {
      expect(() => component.ngAfterViewChecked()).not.toThrow();
    });
  });
});
