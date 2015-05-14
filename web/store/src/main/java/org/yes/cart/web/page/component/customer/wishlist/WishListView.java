/*
 * Copyright 2009 Igor Azarnyi, Denys Pavlov
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package org.yes.cart.web.page.component.customer.wishlist;

import org.apache.commons.collections.CollectionUtils;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.StringResourceModel;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.yes.cart.domain.dto.ProductSearchResultDTO;
import org.yes.cart.domain.entity.CustomerWishList;
import org.yes.cart.domain.entity.ProductAvailabilityModel;
import org.yes.cart.domain.entity.SkuPrice;
import org.yes.cart.domain.misc.Pair;
import org.yes.cart.util.MoneyUtils;
import org.yes.cart.util.ShopCodeContext;
import org.yes.cart.web.application.ApplicationDirector;
import org.yes.cart.web.page.component.price.PriceView;
import org.yes.cart.web.page.component.product.AbstractProductSearchResultList;
import org.yes.cart.web.service.wicketsupport.LinksSupport;
import org.yes.cart.web.support.constants.StorefrontServiceSpringKeys;
import org.yes.cart.web.support.constants.WebParametersKeys;
import org.yes.cart.web.support.service.CustomerServiceFacade;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.*;

/**
 * Show new arrival. Current category will be selected to pick up
 * new arrivals.
 * <p/>
 * User: Igor Azarny iazarny@yahoo.com
 * Date: 9/18/11
 * Time: 11:45 AM
 */
public class WishListView extends AbstractProductSearchResultList {

    @SpringBean(name = StorefrontServiceSpringKeys.CUSTOMER_SERVICE_FACADE)
    private CustomerServiceFacade customerServiceFacade;

    private List<ProductSearchResultDTO> products = null;

    private Map<ProductSearchResultDTO, CustomerWishList> wishListDataByProduct = new HashMap<ProductSearchResultDTO, CustomerWishList>();

    private final Model<String> customerEmail;
    private final Model<String> wishListType;
    private final Model<String> wishListTag;

    private boolean ownerViewing = false;


    /**
     * Construct wish list to show for current user.
     *
     * @param id component id.
     */
    public WishListView(final String id) {
        this(
                id,
                new Model<String>(ApplicationDirector.getShoppingCart().getCustomerEmail()),
                new Model<String>(null),
                new Model<String>(null)
        );
    }


    /**
     * Construct wish list to show for given user.
     *
     * @param id component id.
     *
     * @param customerEmail customer email
     * @param wishListType wishlist type
     * @param wishListTag tags
     */
    public WishListView(final String id,
                        final Model<String> customerEmail,
                        final Model<String> wishListType,
                        final Model<String> wishListTag) {
        super(id, true);
        this.customerEmail = customerEmail;
        this.wishListType = wishListType;
        this.wishListTag = wishListTag;
    }

    @Override
    protected void onBeforeRender() {

        boolean oldOwnerViewing = ownerViewing;
        ownerViewing = !getPage().getPageParameters().getNamedKeys().contains("token") && customerEmail.getObject() != null && customerEmail.getObject().equals(ApplicationDirector.getShoppingCart().getCustomerEmail());
        if (ownerViewing != oldOwnerViewing) {
            products = null; // reset products just in case
        }
        addOrReplace(new Label("noProducts", new StringResourceModel("wishlistNoItems", this, null)).setVisible(getProductListToShow().isEmpty()));
        super.onBeforeRender();
    }

    /** {@inheritDoc} */
    @Override
    public List<ProductSearchResultDTO> getProductListToShow() {
        if (products == null) {

            final List<CustomerWishList> wishList = new ArrayList<CustomerWishList>(customerServiceFacade.getCustomerWishListByEmail(
                    this.wishListType.getObject(), this.customerEmail.getObject(),
                    ownerViewing ? null : CustomerWishList.SHARED, this.wishListTag.getObject() != null ? new String[] { this.wishListTag.getObject() } : null));

            if (CollectionUtils.isNotEmpty(wishList)) {

                final List<String> productIds = new ArrayList<String>();

                for (final CustomerWishList item : wishList) {

                    productIds.add(String.valueOf(item.getSkus().getProduct().getProductId()));

                }

                final List<ProductSearchResultDTO> uniqueProducts = productServiceFacade.getListProducts(
                        productIds, -1L, ShopCodeContext.getShopId());

                final List<ProductSearchResultDTO> wishListProducts = new ArrayList<ProductSearchResultDTO>();

                for (final CustomerWishList item : wishList) {

                    for (final ProductSearchResultDTO uniqueProduct : uniqueProducts) {

                        if (uniqueProduct.getId() == item.getSkus().getProduct().getProductId()) {
                            final ProductSearchResultDTO copy = uniqueProduct.copy();
                            copy.setDefaultSkuCode(item.getSkus().getCode());
                            wishListProducts.add(copy);
                            wishListDataByProduct.put(copy, item);

                        }

                    }

                }

                products = wishListProducts;

            } else {

                products = Collections.emptyList();
            }

        }
        return products;
    }

    @Override
    protected void onBeforeRenderSetVisibility() {
        // do nothing, should be visible always
    }

    @Override
    protected void onBeforeRenderPopulateListItem(final ListItem<ProductSearchResultDTO> listItem,
                                                  final String selectedLocale,
                                                  final Pair<String, String> thumbWidthHeight) {
        super.onBeforeRenderPopulateListItem(listItem, selectedLocale, thumbWidthHeight);

        final LinksSupport links = getWicketSupportFacade().links();

        final ProductSearchResultDTO product = listItem.getModel().getObject();
        final CustomerWishList itemData = wishListDataByProduct.get(product);

        final ProductAvailabilityModel pam = productServiceFacade.getProductAvailability(product, ShopCodeContext.getShopId());

        final SkuPrice priceNow = getSkuPrice(product.getDefaultSkuCode(), itemData.getQuantity());
        final boolean isPriceNowAvailable = priceNow != null && priceNow.getRegularPrice() != null;
        final String currency = priceNow != null ? priceNow.getCurrency() : "";
        final String addedPriceCurr = itemData.getRegularPriceCurrencyWhenAdded();
        final Pair<BigDecimal, BigDecimal> price;
        String priceInfo = "";
        if (isPriceNowAvailable) {
            if (ApplicationDirector.getShoppingCart().getCurrencyCode().equals(addedPriceCurr)) {
                final BigDecimal addedPrice = itemData.getRegularPriceWhenAdded();
                final BigDecimal saleNow = MoneyUtils.minPositive(priceNow.getRegularPrice(), priceNow.getSalePriceForCalculation());
                if (MoneyUtils.isFirstEqualToSecond(addedPrice, saleNow)) {
                    // no change
                    if (MoneyUtils.isFirstBiggerThanSecond(priceNow.getRegularPrice(), addedPrice)) {
                        price = new Pair<BigDecimal, BigDecimal>(priceNow.getRegularPrice(), addedPrice);
                        priceInfo = getLocalizer().getString("wishListPriceOnSaleNow", this, new Model<Object[]>(new Object[] {
                                MoneyUtils.getDiscountDisplayValue(priceNow.getRegularPrice(), addedPrice).toPlainString()
                        }));
                    } else {
                        // not on sale
                        price = new Pair<BigDecimal, BigDecimal>(addedPrice, null);
                    }
                } else if (MoneyUtils.isFirstBiggerThanSecond(addedPrice, saleNow)) {
                    // price dropped since added
                    price = new Pair<BigDecimal, BigDecimal>(addedPrice, saleNow);
                    priceInfo = getLocalizer().getString("wishListPriceDecreased", this, new Model<Object[]>(new Object[] {
                            MoneyUtils.getDiscountDisplayValue(addedPrice, saleNow).toPlainString()
                    }));
                } else {
                    // price gone up
                    price = new Pair<BigDecimal, BigDecimal>(saleNow, null);
                    priceInfo = getLocalizer().getString("wishListPriceIncreased", this, new Model<Object[]>(new Object[] {
                            MoneyUtils.getDiscountDisplayValue(addedPrice, saleNow).negate().toPlainString()
                    }));
                }
            } else {
                // no comparative price - different currency
                price = new Pair<BigDecimal, BigDecimal>(priceNow.getRegularPrice(), priceNow.getSalePriceForCalculation());
            }
        } else {
            // Item is not priced now so it cannot be bought
            price = new Pair<BigDecimal, BigDecimal>(BigDecimal.ZERO, null);
        }

        final String qty = itemData.getQuantity().stripTrailingZeros().toPlainString();

        final boolean simpleWishList = CustomerWishList.SIMPLE_WISH_ITEM.equals(itemData.getWlType());
        final boolean share = CustomerWishList.PRIVATE.equals(itemData.getVisibility());
        final PageParameters visibilityLinks = new PageParameters();
        visibilityLinks.add(WebParametersKeys.PAGE_TYPE, "wishlist");

        listItem.add(
                links.newAddToWishListLink("shareItemLink", product.getDefaultSkuCode(), "0", itemData.getWlType(), null, CustomerWishList.SHARED, visibilityLinks)
                        .setVisible(ownerViewing && simpleWishList && share)
        );
        listItem.add(
                links.newAddToWishListLink("hideItemLink", product.getDefaultSkuCode(), "0", itemData.getWlType(), null, CustomerWishList.PRIVATE, visibilityLinks)
                        .setVisible(ownerViewing && simpleWishList && !share)
        );

        listItem.add(
                determineAtbLink(links, "addToCardFromWishListLink", product, itemData, qty)
                        .add(new Label("addToCardFromWishListLinkLabel", pam.isInStock() || pam.isPerpetual() ?
                                getLocalizer().getString("addToCartWlBtn", this, new Model<Serializable>(new String[] { qty })) :
                                getLocalizer().getString("preorderCartWlBtn", this, new Model<Serializable>(new String[] { qty }))))
                        .setVisible(pam.isAvailable())
        );

        listItem.add(
                links.newRemoveFromWishListLink("removeFromWishListLink", product.getDefaultSkuCode(), itemData.getCustomerwishlistId(), (Class) getPage().getClass(), null)
                        .add(new Label("removeFromWishListLinkLabel", getLocalizer().getString("removeFromWishlist", this)))
                                .setVisible(ownerViewing)
        );

        listItem.add(
                new PriceView("priceView", price, currency, priceInfo, true, false).setVisible(isPriceNowAvailable)
        );


        listItem.add(new AttributeModifier("data-tag", itemData.getTag()));
        listItem.add(new AttributeModifier("data-visibility", itemData.getVisibility()));
        listItem.add(new AttributeModifier("data-type", itemData.getWlType()));
        listItem.add(new AttributeModifier("data-sku", product.getDefaultSkuCode()));
        listItem.add(new AttributeModifier("data-qty", itemData.getQuantity().toPlainString()));

    }

    /**
     * Extension hook for sub classes.
     *
     * @param links links support
     * @param linkId link id
     * @param product product
     * @param itemData wish list item
     * @param qty quantity as string
     *
     * @return link
     */
    protected Link determineAtbLink(final LinksSupport links,
                                    final String linkId,
                                    final ProductSearchResultDTO product,
                                    final CustomerWishList itemData,
                                    final String qty) {
        final PageParameters params = new PageParameters();
        params.add(WebParametersKeys.SKU_ID, itemData.getSkus().getSkuId());
        return links.newAddToCartLink(linkId, product.getDefaultSkuCode(), qty, params);
    }



    /**
     * Get product or his sku price.
     * In case of multisku product the minimal regular price from multiple sku was used for single item.
     *
     *
     * @param firstAvailableSkuCode first available sku code.
     * @param quantity quantity of wish list item
     *
     * @return {@link org.yes.cart.domain.entity.SkuPrice}
     */
    private SkuPrice getSkuPrice(final String firstAvailableSkuCode, final BigDecimal quantity) {
        return productServiceFacade.getSkuPrice(
                null,
                firstAvailableSkuCode,
                quantity,
                ApplicationDirector.getShoppingCart().getCurrencyCode(),
                ApplicationDirector.getCurrentShop().getShopId()
        );
    }

}
