import { Component, OnInit, signal, OnDestroy, ViewChild, ElementRef, AfterViewChecked } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { MessagingService } from '../../core/services/messaging.service';
import { UserService } from '../../core/services/user.service';
import { MessageResponse, UserResponse } from '../../models';

interface ConversationPartner {
  userId: number;
  name: string;
  email: string;
  lastMessage: string;
  lastMessageTime: string;
}

@Component({
  selector: 'app-messages',
  imports: [CommonModule, FormsModule],
  templateUrl: './messages.html',
  styleUrl: './messages.css'
})
export class MessagesComponent implements OnInit, OnDestroy, AfterViewChecked {
  @ViewChild('messagesContainer') private messagesContainer!: ElementRef;
  
  partners       = signal<ConversationPartner[]>([]);
  selectedPartner = signal<ConversationPartner | null>(null);
  messages       = signal<MessageResponse[]>([]);
  allUsers       = signal<UserResponse[]>([]);
  loading        = signal(true);
  sendingMessage = signal(false);
  showUserSelector = signal(false);
  errorMsg       = signal('');
  successMsg     = signal('');
  messageContent = '';
  private pollInterval: any;

  constructor(
    public authService: AuthService,
    private messagingService: MessagingService,
    private userService: UserService,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    const targetUserId = this.route.snapshot.queryParamMap.get('user');
    this.loadConversations(targetUserId ? Number(targetUserId) : undefined);
  }

  ngOnDestroy(): void {
    if (this.pollInterval) {
      clearInterval(this.pollInterval);
    }
  }

  ngAfterViewChecked(): void {
    this.scrollToBottom();
  }

  private scrollToBottom(): void {
    try {
      if (this.messagesContainer) {
        this.messagesContainer.nativeElement.scrollTop = this.messagesContainer.nativeElement.scrollHeight;
      }
    } catch(err) { }
  }

  loadConversations(targetUserId?: number): void {
    this.loading.set(true);
    this.messagingService.getPartnerIds().subscribe({
      next: env => {
        const ids = env.data ?? [];
        if (ids.length === 0 && !targetUserId) { this.loading.set(false); return; }
        
        // Fetch user details for each partner id
        const fetches = ids.map(id =>
          new Promise<UserResponse | null>(resolve => {
            this.userService.getUser(id).subscribe({
              next: uenv => resolve(uenv.data),
              error: () => resolve(null)
            });
          })
        );
        
        Promise.all(fetches).then(users => {
          const validPartners: ConversationPartner[] = users
            .filter((u): u is UserResponse => u !== null)
            .map(u => ({ userId: u.userId, name: u.name ?? u.email, email: u.email, lastMessage: '', lastMessageTime: '' }));
          this.partners.set(validPartners);
          
          if (targetUserId) {
            const existing = validPartners.find(p => p.userId === targetUserId);
            if (existing) {
              this.selectPartner(existing);
              this.loading.set(false);
            } else {
              this.userService.getUser(targetUserId).subscribe({
                next: (uenv) => {
                  if (uenv.data) this.startConversationWith(uenv.data);
                  this.loading.set(false);
                },
                error: () => this.loading.set(false)
              });
            }
          } else if (validPartners.length > 0) {
            this.selectPartner(validPartners[0]);
            this.loading.set(false);
          } else {
            this.loading.set(false);
          }
        });
      },
      error: () => { this.errorMsg.set('Failed to load conversations.'); this.loading.set(false); }
    });
  }

  selectPartner(partner: ConversationPartner): void {
    this.selectedPartner.set(partner);
    this.messageContent = '';
    this.showUserSelector.set(false);
    this.loadMessages(partner.userId);
    
    // Start live polling for this conversation
    if (this.pollInterval) clearInterval(this.pollInterval);
    this.pollInterval = setInterval(() => {
      if (this.selectedPartner()?.userId === partner.userId) {
        this.loadMessages(partner.userId);
      }
    }, 2000);
  }

  loadMessages(partnerId: number): void {
    this.messagingService.getConversation(partnerId).subscribe({
      next: env => this.messages.set(env.data ?? []),
      error: () => this.errorMsg.set('Failed to load messages.')
    });
  }

  sendMessage(): void {
    const content = this.messageContent.trim();
    const partner = this.selectedPartner();
    if (!content) { this.errorMsg.set('Message cannot be empty.'); return; }
    if (!partner)  { this.errorMsg.set('Please select a conversation.'); return; }

    this.sendingMessage.set(true);
    this.errorMsg.set('');

    this.messagingService.sendMessage(partner.userId, content).subscribe({
      next: env => {
        this.messageContent = '';
        this.sendingMessage.set(false);
        if (env.data) {
          this.messages.update(list => [...list, env.data!]);
        }
      },
      error: env => {
        this.sendingMessage.set(false);
        this.errorMsg.set(env.error ?? 'Failed to send message.');
      }
    });
  }

  loadAllUsers(): void {
    if (this.allUsers().length === 0) {
      const currentUserId = this.authService.userId();
      this.userService.getAllUsers().subscribe({
        next: env => {
          this.allUsers.set((env.data ?? []).filter(u => u.userId !== currentUserId));
        }
      });
    }
    this.showUserSelector.update(v => !v);
  }

  startConversationWith(user: UserResponse): void {
    const partner: ConversationPartner = {
      userId: user.userId, name: user.name ?? user.email, email: user.email,
      lastMessage: '', lastMessageTime: ''
    };
    // Add to partners list if not already present
    const exists = this.partners().some(p => p.userId === user.userId);
    if (!exists) {
      this.partners.update(list => [partner, ...list]);
    }
    this.selectPartner(partner);
  }

  isCurrentUser(senderId: number): boolean {
    return senderId === this.authService.userId();
  }

  formatTime(date: string): string {
    return new Date(date).toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' });
  }

  formatDate(date: string): string {
    return new Date(date).toLocaleDateString('en-IN', { year: 'numeric', month: 'short', day: 'numeric' });
  }
}
