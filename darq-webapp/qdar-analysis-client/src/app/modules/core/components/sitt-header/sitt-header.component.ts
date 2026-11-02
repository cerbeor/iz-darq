import {Component, Input} from '@angular/core';
import {IServerInfo} from '../../services/app-info.service';

@Component({
  selector: 'app-sitt-header',
  templateUrl: './sitt-header.component.html',
  // Styles come from the global src/sitt-styling.css (see angular.json), which
  // is kept byte-identical to the shared brand sheet. App specific tweaks go in
  // a separate sheet rather than in that copy.
  styles: [],
})
export class SittHeaderComponent {

  @Input()
  info: IServerInfo;

}
