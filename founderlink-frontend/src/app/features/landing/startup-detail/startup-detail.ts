import { Component, OnInit, signal, HostBinding } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { StartupService } from '../../../core/services/startup.service';
import { AuthService } from '../../../core/services/auth.service';
import { StartupResponse, StartupStage } from '../../../models';
import { ThemeService } from '../../../core/services/theme.service';

@Component({
  selector: 'app-startup-detail',
  imports: [CommonModule, RouterLink],
  templateUrl: './startup-detail.html',
  styleUrl: './startup-detail.css'
})
export class StartupDetailComponent implements OnInit {
  @HostBinding('class.crystal-mode') 
  get isCrystalMode() { return this.themeService.isCrystal(); }

  startup = signal<StartupResponse | null>(null);
  loading = signal(true);
  error   = signal('');

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private startupService: StartupService,
    public  authService: AuthService,
    public  themeService: ThemeService
  ) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.startupService.getDetails(id).subscribe({
      next:  env => { this.startup.set(env.data); this.loading.set(false); },
      error: ()  => { this.error.set('Startup not found.'); this.loading.set(false); }
    });
  }

  invest(): void {
    if (!this.authService.isLoggedIn()) {
      this.router.navigate(['/auth/login']);
    } else {
      this.router.navigate(['/dashboard/startups']);
    }
  }

  goBack(): void { 
    if (this.authService.isLoggedIn()) {
      this.router.navigate(['/dashboard/startups']);
    } else {
      this.router.navigate(['/']); 
    }
  }

  stageLabel(stage: StartupStage): string {
    const map: Record<string, string> = {
      IDEA: 'Idea Stage', MVP: 'MVP', EARLY_TRACTION: 'Early Traction', SCALING: 'Scaling'
    };
    return map[stage] ?? stage;
  }

  stageClass(stage: StartupStage): string {
    return stage === 'IDEA'           ? 'stage-idea'
         : stage === 'MVP'            ? 'stage-mvp'
         : stage === 'EARLY_TRACTION' ? 'stage-traction'
         : 'stage-scaling';
  }

  formatCurrency(n: number): string {
    if (!n) return '₹0';
    if (n >= 10_000_000) return `₹${(n / 10_000_000).toFixed(2)}Cr`;
    if (n >= 100_000)    return `₹${(n / 100_000).toFixed(2)}L`;
    return `₹${n.toLocaleString('en-IN')}`;
  }
}
