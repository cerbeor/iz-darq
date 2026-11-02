import {Observable} from 'rxjs';
import {Store} from '@ngrx/store';
import {map} from 'rxjs/operators';
import {Component, Input, OnInit,} from '@angular/core';
import {LogoutRequest, selectIsAdmin, selectIsLoggedIn, selectRouterURL} from '@usnistgov/ngx-dam-framework-legacy';
import {IServerInfo} from '../../services/app-info.service';
import {ICurrentUser} from '../../model/user.model';
import {selectCurrentUser} from '../../store/core.selectors';

@Component({
  selector: 'app-header',
  templateUrl: './header.component.html',
  // sitt-styling.css is global page chrome and is registered in angular.json.
  // It must not be listed here: view encapsulation would scope its selectors
  // to this component, so the #footer rules could never reach the footer.
  styleUrls: ['./header.component.scss']
})
export class HeaderComponent implements OnInit {

  @Input()
  info: IServerInfo;

  isAdf: Observable<boolean>;
  isAdmin$: Observable<boolean>;
  isLogged$: Observable<boolean>;
  currentUser$: Observable<ICurrentUser>;

  constructor(private store: Store<any>) {
    this.isLogged$ = store.select(selectIsLoggedIn);
    this.currentUser$ = store.select(selectCurrentUser);
    this.isAdf = store.select(selectRouterURL).pipe(
      map(
        (url: string) => {
          return url.startsWith('/adf/');
        },
      ),
    );
    this.isAdmin$ = this.store.select(selectIsAdmin);
  }

  logout() {
    this.store.dispatch(new LogoutRequest());
  }

  ngOnInit(): void {
  }

}
