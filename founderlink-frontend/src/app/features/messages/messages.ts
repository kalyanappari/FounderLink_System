import { Component, OnInit, signal, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
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
export class MessagesComponent implements OnInit {
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

  constructor(
    public authService: AuthService,
    private messagingService: MessagingService,
    private userService: UserService
  ) {}

  ngOnInit(): void {
    this.loadConversations();
  }

  loadConversations(): void {
    this.loading.set(true);
    this.messagingService.getPartnerIds().subscribe({
      next: env => {
        const ids = env.data ?? [];
        if (ids.length === 0) { this.loading.set(false); return; }
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
          if (validPartners.length > 0) {
            this.selectPartner(validPartners[0]);
          }
          this.loading.set(false);
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
