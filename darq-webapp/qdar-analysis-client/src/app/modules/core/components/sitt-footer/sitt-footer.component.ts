import {Component} from '@angular/core';

@Component({
  selector: 'app-sitt-footer',
  templateUrl: './sitt-footer.component.html',
  // The brand chrome itself comes from the global src/sitt-styling.css (see
  // angular.json); this sheet holds only the qDAR specific tweaks on top.
  styleUrls: ['./sitt-footer.component.scss'],
})
export class SittFooterComponent {
}
